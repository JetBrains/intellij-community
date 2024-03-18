// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.appendonlylog;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.newvfs.persistent.dev.appendonlylog.AppendOnlyLogOverMMappedFile.HeaderLayout;
import com.intellij.util.io.dev.mmapped.MMappedFileStorageFactory;
import com.intellij.util.io.CorruptedException;
import com.intellij.util.io.VersionUpdatedException;
import com.intellij.util.io.dev.StorageFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.intellij.util.io.IOUtil.*;
import static java.nio.ByteOrder.nativeOrder;
import static java.nio.file.StandardOpenOption.READ;

/**
 * Configurable factory for {@link AppendOnlyLogOverMMappedFile}.
 * All major params and initialization conditions, such as dataFormatVersion check, recovery -- are configurable
 */
@ApiStatus.Internal
public class AppendOnlyLogFactory implements StorageFactory<AppendOnlyLogOverMMappedFile> {
  private static final Logger LOG = Logger.getInstance(AppendOnlyLogFactory.class);

  public static final int DEFAULT_PAGE_SIZE = 4 * MiB;


  private final int pageSize;

  private final int expectedDataVersion;
  private final boolean ensureDataVersion;

  /** Fail if recovery was needed */
  private final boolean failInsteadOfRecovery;

  private final boolean eagerlyCheckFileCompatibility;
  /**
   * If eager check finds file is incompatible (magic-word, impl version, page size):
   * true: just clean/delete it and open empty storage on top of empty file
   * false: throw an IOException
   * Beware: data version check is now separated
   */
  private final boolean cleanFileIfIncompatible;


  private AppendOnlyLogFactory(int pageSize,
                               boolean ensureDataVersion,
                               int expectedDataVersion,
                               boolean failInsteadOfRecovery,
                               boolean eagerlyCheckFileCompatibility,
                               boolean cleanFileIfIncompatible) {
    this.pageSize = pageSize;
    this.expectedDataVersion = expectedDataVersion;
    this.ensureDataVersion = ensureDataVersion;
    this.eagerlyCheckFileCompatibility = eagerlyCheckFileCompatibility;
    this.failInsteadOfRecovery = failInsteadOfRecovery;
    this.cleanFileIfIncompatible = cleanFileIfIncompatible;
  }

  public static AppendOnlyLogFactory withDefaults() {
    return new AppendOnlyLogFactory(
      DEFAULT_PAGE_SIZE,
      /* ensureDataVersion:         */ false, 0,
      /* failInsteadOfRecovery:     */ false,
      /* eagerlyCheckCompatibility: */ true,
      /* cleanIncompatibleFiles:    */ false
    );
  }

  public AppendOnlyLogFactory pageSize(int pageSize) {
    return new AppendOnlyLogFactory(
      pageSize,
      ensureDataVersion, expectedDataVersion,
      failInsteadOfRecovery, eagerlyCheckFileCompatibility, cleanFileIfIncompatible
    );
  }

  public AppendOnlyLogFactory failIfDataFormatVersionNotMatch(int expectedDataVersion) {
    return new AppendOnlyLogFactory(
      pageSize,
      /*ensureDataVersion: */ true, expectedDataVersion,
      failInsteadOfRecovery, eagerlyCheckFileCompatibility, cleanFileIfIncompatible
    );
  }

  public AppendOnlyLogFactory ignoreDataFormatVersion() {
    return new AppendOnlyLogFactory(
      pageSize,
      /*ensureDataVersion: */ false, /*expectedDataVersion: */ 0,
      failInsteadOfRecovery, eagerlyCheckFileCompatibility, cleanFileIfIncompatible
    );
  }

  /** Fail if recovery was needed */
  public AppendOnlyLogFactory dontRecoverFailInstead() {
    return new AppendOnlyLogFactory(
      pageSize, ensureDataVersion, expectedDataVersion,
      /* dontRecoverFailInstead: */ true,
      eagerlyCheckFileCompatibility,
      cleanFileIfIncompatible
    );
  }

  public AppendOnlyLogFactory checkIfFileCompatibleEagerly(boolean eagerlyCheckCompatibility) {
    return new AppendOnlyLogFactory(
      pageSize, ensureDataVersion, expectedDataVersion, failInsteadOfRecovery, eagerlyCheckCompatibility,
      cleanFileIfIncompatible
    );
  }

  public AppendOnlyLogFactory cleanIfFileIncompatible() {
    return new AppendOnlyLogFactory(
      pageSize, ensureDataVersion, expectedDataVersion, failInsteadOfRecovery,
      /* eagerlyCheckFileCompatibility: */ true,
      /* cleanFileIfIncompatible:       */ true
    );
  }

  public AppendOnlyLogFactory failFileIfIncompatible() {
    return new AppendOnlyLogFactory(
      pageSize, ensureDataVersion, expectedDataVersion, failInsteadOfRecovery,
      /* eagerlyCheckFileCompatibility: */ true,
      /* cleanFileIfIncompatible:       */ false
    );
  }


  @Override
  public @NotNull AppendOnlyLogOverMMappedFile open(@NotNull Path storagePath) throws IOException {
    if (eagerlyCheckFileCompatibility) {
      //Check the crucial file params (file type, impl version, page size...) _before_ open mmapped storage over it.
      // It could be troubling to unmap & delete file already mapped into memory (especially on Windows), so it
      // pays off to check crucial file parameters eagerly, before the mapping, and either fail or clean the file
      // while it is not mapped yet:
      long size = Files.exists(storagePath) ? Files.size(storagePath) : 0L;
      if (size > 0) {
        ByteBuffer buffer = ByteBuffer.allocate(HeaderLayout.HEADER_SIZE)
          .order(nativeOrder())
          .clear();

        try (FileChannel channel = FileChannel.open(storagePath, READ)) {
          int actuallyRead = channel.read(buffer);
          if (actuallyRead != HeaderLayout.HEADER_SIZE) {
            throw new CorruptedException("[" + storagePath + "]: file is not empty, but < HEADER_SIZE(=" + HeaderLayout.HEADER_SIZE + ")");
          }
          AppendOnlyLogOverMMappedFile.checkFileParamsCompatible(storagePath, buffer, pageSize);
          //TODO RC: maybe .expectedDataVersion check also better be here?
        }
        catch (IOException ex) {
          if (cleanFileIfIncompatible) {
            LOG.warn("[" + storagePath + "] is incompatible with current format " +
                     "-> delete it, and pretend never seen it incompatible " +
                     "(incompatibility: " + ex.getMessage() + ")"
            );
            FileUtil.delete(storagePath);
          }
          else {
            throw ex;
          }
        }
      }
    }

    MMappedFileStorageFactory mappedFileStorageFactory = MMappedFileStorageFactory.withDefaults()
      .pageSize(pageSize);
    return mappedFileStorageFactory.wrapStorageSafely(
      storagePath,
      storage -> {
        AppendOnlyLogOverMMappedFile appendOnlyLog = new AppendOnlyLogOverMMappedFile(storage);

        if (failInsteadOfRecovery) {
          if (appendOnlyLog.wasRecoveryNeeded()) {
            throw new CorruptedException("[" + storagePath.toAbsolutePath() + "] wasn't properly closed, " +
                                         "and recovery is prohibited -> fail");
          }
        }

        if (ensureDataVersion) {
          int dataFormatVersion = appendOnlyLog.getDataVersion();
          if (dataFormatVersion == 0 && appendOnlyLog.isEmpty()) {
            appendOnlyLog.setDataVersion(expectedDataVersion);
          }
          else if (dataFormatVersion != expectedDataVersion) {
            throw new VersionUpdatedException(storagePath, expectedDataVersion, dataFormatVersion);
          }
        }

        return appendOnlyLog;
      }
    );
  }

  @Override
  public String toString() {
    return "AppendOnlyLogFactory{" +
           "pageSize=" + pageSize +
           (ensureDataVersion ? ", ensure data version: " + expectedDataVersion : "") +
           ", failInsteadOfRecovery=" + failInsteadOfRecovery +
           ", eagerlyCheckFileCompatibility=" + eagerlyCheckFileCompatibility +
           ", cleanFileIfIncompatible=" + cleanFileIfIncompatible +
           '}';
  }
}
