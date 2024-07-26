// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.newvfs.persistent.recovery.ContentStoragesRecoverer;
import com.intellij.openapi.vfs.newvfs.persistent.recovery.NotClosedProperlyRecoverer;
import com.intellij.openapi.vfs.newvfs.persistent.recovery.VFSInitializationResult;
import com.intellij.openapi.vfs.newvfs.persistent.recovery.VFSRecoverer;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.io.CorruptedException;
import com.intellij.util.io.StorageAlreadyInUseException;
import com.intellij.util.io.VersionUpdatedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.vfs.newvfs.persistent.VFSInitException.ErrorCategory.*;
import static com.intellij.util.ExceptionUtil.findCauseAndSuppressed;

/**
 * Static helper responsible for 'connecting' (opening, initializing) {@linkplain PersistentFSConnection} object,
 * and closing it. It does a few tries to initialize VFS storages, tries to correct/rebuild broken parts, and so on.
 */
final class PersistentFSConnector {
  private static final Logger LOG = Logger.getInstance(PersistentFSConnector.class);

  /**
   * FUS data shows no successful initialization takes >2 attempts -- 1st one fails by
   * some reason, and the 2nd one rebuilds VFS from 0.
   * The only scenario with >2 attempts is there something goes completely wrong, and
   * initialization finally fails even after 10 attempts. But in such a scenario, more
   * attempts do no good, but plague the logs with more exception traces, making it
   * harder to spot the root cause.
   * Hence, max=3 attempts is our smart, data-informed choice:
   */
  private static final int MAX_INITIALIZATION_ATTEMPTS = SystemProperties.getIntProperty("vfs.max-initialization-attempts", 3);

  public static final List<VFSRecoverer> RECOVERERS = List.of(
    //order is important: wrap the set of problems -> try to solve them.
    new NotClosedProperlyRecoverer(),
    new ContentStoragesRecoverer()
  );

  public static @NotNull VFSInitializationResult connect(@NotNull Path cachesDir,
                                                         int version) {
    return init(cachesDir, version);
  }

  //TODO RC: why do we need the method? -- better call connection.close() directly
  public static void disconnect(@NotNull PersistentFSConnection connection) throws IOException {
    connection.close();
  }

  //=== internals:

  private static @NotNull VFSInitializationResult init(@NotNull Path cachesDir,
                                                       int expectedVersion) {
    List<Throwable> attemptsFailures = new ArrayList<>();
    long initializationStartedNs = System.nanoTime();
    for (int attempt = 0; attempt < MAX_INITIALIZATION_ATTEMPTS; attempt++) {
      try {
        PersistentFSConnection connection = tryInit(
          cachesDir,
          expectedVersion,
          RECOVERERS
        );
        //heuristics: just created VFS contains only 1 record (=super-root):
        boolean justCreated = connection.getRecords().recordsCount() == 1
                              && connection.isDirty();

        return new VFSInitializationResult(
          connection,
          justCreated,
          attemptsFailures,
          System.nanoTime() - initializationStartedNs
        );
      }
      catch (Exception e) {
        LOG.info("Init VFS attempt #" + attempt + " failed: " + e.getMessage());

        attemptsFailures.add(e);
      }
    }

    RuntimeException fail = new RuntimeException("VFS can't be initialized (" + MAX_INITIALIZATION_ATTEMPTS + " attempts failed)");
    for (Throwable failure : attemptsFailures) {
      fail.addSuppressed(failure);
    }
    throw fail;
  }

  @VisibleForTesting
  static @NotNull PersistentFSConnection tryInit(@NotNull Path cachesDir,
                                                 int currentImplVersion,
                                                 @NotNull List<VFSRecoverer> recoverers) throws IOException {
    //RC: Mental model behind VFS initialization:
    //   VFS consists of few different storages: records, attributes, content... Each storage has its own on-disk
    //   data format. Each storage also has a writeable header field .version, which is (must be) 0 by default.
    //   VFS as a whole defines 'implementation version' (currentImplVersion) which is the overall on-disk data
    //   format known to current VFS code.
    //
    //   When VFS is initialized from scratch, this 'VFS impl version' is stamped into each storage .version header field.
    //   When VFS is loaded (from already existing on-disk representation), we load each storage from apt file(s), collect
    //   all the storage.versions and check them against currentImplVersion -- and if all the versions are equal to
    //   currentImplVersion => VFS is OK and ready to use ('success path')
    //
    //   If not all versions are equal to currentImplVersion => we assume VFS is outdated, drop all the VFS files, and throw
    //   exception, which most likely triggers VFS rebuild from 0 somewhere above.
    //   Now, versions could be != currentImplVersion for different reasons:
    //   1) On-disk VFS are of an ancient version -- the most obvious reason
    //   2) Some of the VFS storages are missed on disk, and initialized from 0, thus having their .version=0
    //   3) Some of the VFS storages have an unrecognizable on-disk format (e.g. corrupted) -- in this case
    //      specific storage implementation could either
    //      a) re-create storage from 0 => will have .version=0, see branch #2
    //      b) throw an exception => will be caught, and VFS files will be all dropped,
    //                               and likely lead to VFS rebuild from 0 somewhere upper the callstack
    //   So the simple condition (all storages .version must be == currentImplVersion) actually covers a set of different cases
    //   which is convenient.
    //
    //MAYBE RC: Current model has important drawback: it is all-or-nothing, i.e. even a minor change in VFS on-disk format requires
    //          full VFS rebuild. VFS rebuild itself is not so costly -- but it invalidates all fileIds, which causes Indexes rebuild,
    //          which IS costly.
    //          It would be nicer if VFS be able to just 'upgrade' a minor change in format to a newer version, without full rebuild
    //          -- but with the current approach such functionality, it is hard to plug in.
    //          Sketch of a better implementation:
    //          1) VFS currentImplVersion is stored in a single dedicated place, in 'version.txt' file (human-readable)
    //          2) Each VFS storage manages its own on-disk-format-version -- i.e. each storage has its own CURRENT_IMPL_VERSION,
    //             which it stores somewhere in a file(s) header. And each storage is responsible for detecting on-disk version,
    //             and either read the known format, or read-and-silently-upgrade the known but slightly outdated format, or throw
    //             an error if it can't deal with on-disk data at all.
    //          VFS.currentImplVersion is changed only on a major format changes, there implement 'upgrade' is too costly, and full
    //          rebuild is the way to go. Another reason for full rebuild is if any of storages is completely confused by on-disk
    //          data (=likely corruption or too old data format). In other cases, VFS could be upgraded 'under the carpet' without
    //          invalidating fileId. Hence, Indexes don't need to be rebuilt.

    Path basePath = cachesDir.toAbsolutePath();
    Files.createDirectories(basePath);

    //MAYBE RC: looks like it all could be much easier with coroutines
    VFSAsyncTaskExecutor pool = PersistentFsConnectorHelper.INSTANCE.executor();

    PersistentFSPaths persistentFSPaths = new PersistentFSPaths(cachesDir);
    PersistentFSLoader vfsLoader = new PersistentFSLoader(persistentFSPaths, pool);
    try {
      vfsLoader.failIfCorruptionMarkerPresent();

      vfsLoader.initializeStorages();

      vfsLoader.ensureStoragesVersionsAreConsistent(currentImplVersion);

      boolean needInitialization = vfsLoader.isJustCreated();

      if (needInitialization) {
        // Create root record:
        int rootRecordId = vfsLoader.recordsStorage().allocateRecord();
        if (rootRecordId != FSRecords.ROOT_FILE_ID) {
          throw new AssertionError("First record created must have id=" + FSRecords.ROOT_FILE_ID + " but " + rootRecordId + " got instead");
        }
        vfsLoader.recordsStorage().cleanRecord(rootRecordId);
      }
      else {
        vfsLoader.selfCheck();

        if (!vfsLoader.problemsDuringLoad().isEmpty()) {
          for (VFSRecoverer recoverer : recoverers) {
            recoverer.tryRecover(vfsLoader);
          }

          //Were all problems recovered? -> fail if not
          List<VFSInitException> problemsNotRecovered = vfsLoader.problemsDuringLoad();
          if (!problemsNotRecovered.isEmpty()) {
            VFSInitException mainEx = problemsNotRecovered.get(0);
            for (int i = 1; i < problemsNotRecovered.size(); i++) {
              mainEx.addSuppressed(problemsNotRecovered.get(i));
            }
            throw mainEx;
          }
        }
      }

      PersistentFSConnection connection = vfsLoader.createConnection();

      if (needInitialization) {//make just-initialized connection dirty (i.e. it must be saved)
        connection.markDirty();
      }

      return connection;
    }
    catch (Throwable e) { // IOException, IllegalArgumentException, AssertionError
      String errorMessage = ExceptionUtil.getNonEmptyMessage(e, "<unrecognized>");
      LOG.warn("Filesystem storage is corrupted or does not exist. [Re]Building. Reason: " + errorMessage);
      try {
        vfsLoader.closeEverything();

        List<StorageAlreadyInUseException> storageAlreadyInUseExceptions = findCauseAndSuppressed(e, StorageAlreadyInUseException.class);
        if(!storageAlreadyInUseExceptions.isEmpty()){
          //some of the storages are used by another process: don't clean VFS (it doesn't help), interrupt startup instead
          throw new IOException("Some of VFS storages are already in use: is an IDE process already running?", e);
        }

        vfsLoader.deleteEverything();
      }
      catch (IOException cleanEx) {
        e.addSuppressed(cleanEx);
        LOG.warn("Cannot clean filesystem storage", cleanEx);
      }

      //Try to unwrap exception, so the real cause appears, we could throw VFSNeedsRebuildException with it:

      List<VFSInitException> vfsNeedsRebuildExceptions = findCauseAndSuppressed(e, VFSInitException.class);
      if (!vfsNeedsRebuildExceptions.isEmpty()) {
        VFSInitException mainEx = vfsNeedsRebuildExceptions.get(0);
        for (VFSInitException suppressed : vfsNeedsRebuildExceptions.subList(1, vfsNeedsRebuildExceptions.size())) {
          mainEx.addSuppressed(suppressed);
        }
        throw mainEx;
      }

      //VersionUpdatedException extends CorruptedException, so we must look for VersionUpdated first:
      if (!findCauseAndSuppressed(e, VersionUpdatedException.class).isEmpty()) {
        throw new VFSInitException(IMPL_VERSION_MISMATCH, "Some of storages versions were changed", e);
      }

      if (!findCauseAndSuppressed(e, CorruptedException.class).isEmpty()) {
        //'not closed properly' is the most likely explanation of corrupted enumerator -- but not the only one,
        // it could also be a code bug
        throw new VFSInitException(NOT_CLOSED_PROPERLY, "Some of storages were corrupted", e);
      }

      throw new VFSInitException(UNRECOGNIZED, "VFS init failure of unrecognized category: " + errorMessage, e);
    }
  }
}
