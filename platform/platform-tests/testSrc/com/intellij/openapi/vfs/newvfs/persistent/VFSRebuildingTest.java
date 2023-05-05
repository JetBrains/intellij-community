// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordsStorageFactory.RecordsStorageKind;
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsLog;
import com.intellij.testFramework.TemporaryDirectory;
import com.intellij.util.io.PageCacheUtils;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordsStorageFactory.RecordsStorageKind.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.*;

/**
 * Test VFS version management and VFS rebuild on implementation version change
 */
public class VFSRebuildingTest {

  @Rule
  public final TemporaryDirectory temporaryDirectory = new TemporaryDirectory();

  @Test
  public void connection_ReopenedWithSameVersion_HasDataFromPreviousTurn() throws IOException {
    final Path cachesDir = temporaryDirectory.createDir();
    final int version = 1;

    final PersistentFSConnection connection = PersistentFSConnector.tryInit(
      cachesDir,
      version,
      true,
      new InvertedNameIndex(),
      Collections.emptyList()
    );
    final PersistentFSRecordsStorage records = connection.getRecords();
    assertEquals(
      "connection.records.version == tryInit(version)",
      records.getVersion(),
      version
    );
    //create few dummy records -- so we could check them exist after reopen:
    createRecords(connection, 3);
    final int recordsCountBeforeClose = records.recordsCount();

    PersistentFSConnector.disconnect(connection);

    final PersistentFSConnection reopenedConnection = PersistentFSConnector.tryInit(
      cachesDir,
      version,
      true,
      new InvertedNameIndex(),
      Collections.emptyList()
    );

    assertEquals(
      "VFS should not be rebuild -- it should successfully load persisted version from disk",
      reopenedConnection.getRecords().recordsCount(),
      recordsCountBeforeClose
    );
  }

  @Test
  public void connection_ReopenedWithSameVersion_HasTimestampFromPreviousTurn() throws IOException, InterruptedException {
    final Path cachesDir = temporaryDirectory.createDir();
    final int version = 1;

    final PersistentFSConnection connection = PersistentFSConnector.tryInit(
      cachesDir,
      version,
      true,
      new InvertedNameIndex(),
      Collections.emptyList()
    );
    final PersistentFSRecordsStorage records = connection.getRecords();
    assertEquals(
      "connection.records.version == tryInit(version)",
      records.getVersion(),
      version
    );
    //create few dummy records
    createRecords(connection, 3);
    final long fsRecordsCreationTimestampBeforeDisconnect = records.getTimestamp();

    PersistentFSConnector.disconnect(connection);

    Thread.sleep(1000);

    final PersistentFSConnection reopenedConnection = PersistentFSConnector.tryInit(
      cachesDir,
      version,
      true,
      new InvertedNameIndex(),
      Collections.emptyList()
    );

    assertEquals(
      "VFS should NOT be rebuild -- reopened VFS should have creation timestamp of VFS before disconnect",
      reopenedConnection.getRecords().getTimestamp(),
      fsRecordsCreationTimestampBeforeDisconnect
    );
  }

  @Test
  public void connection_ReopenedWithDifferentVersion_Fails() throws IOException {
    final Path cachesDir = temporaryDirectory.createDir();
    final int version = 1;
    final PersistentFSConnection connection = PersistentFSConnector.tryInit(
      cachesDir,
      version,
      true,
      new InvertedNameIndex(),
      Collections.emptyList()
    );
    assertEquals(
      "connection.records.version == tryInit(version)",
      version,
      connection.getRecords().getVersion()
    );
    PersistentFSConnector.disconnect(connection);


    final int differentVersion = version + 1;
    try {
      PersistentFSConnector.tryInit(
        cachesDir,
        differentVersion,
        true,
        new InvertedNameIndex(),
        Collections.emptyList()
      );
      fail(
        "VFS opening must fail, since the supplied 'current' version is different from that was used to initialize on-disk structures before");
    }
    catch (IOException e) {
      assertTrue(
        "Exception message must be something about VFS version mismatch",
        e.getMessage().contains("VFS")
      );
    }
  }

  @Test
  public void connection_corruptionMarkerFileIsCreatedOnAsk_AndContainCorruptionReasonAndCauseExceptionTrace() throws IOException {
    Path cachesDir = temporaryDirectory.createDir();

    final String corruptionReason = "VFS corrupted because I said so";
    final String corruptionCauseMessage = "Something happens here";

    PersistentFSConnection connection = PersistentFSConnector.connect(
      cachesDir,
      /*version: */ 1,
      true,
      new InvertedNameIndex(),
      Collections.emptyList()
    );
    final Path corruptionMarkerFile = connection.getPersistentFSPaths().getCorruptionMarkerFile();
    try {
      connection.scheduleVFSRebuild(
        corruptionReason,
        new Exception(corruptionCauseMessage)
      );
    }
    finally {
      PersistentFSConnector.disconnect(connection);
    }

    assertTrue(
      "Corruption marker file " + corruptionMarkerFile + " must be created",
      Files.exists(corruptionMarkerFile)
    );

    final String corruptingMarkerContent = Files.readString(corruptionMarkerFile, UTF_8);
    assertTrue(
      "Corruption file must contain corruption reason [" + corruptionReason + "]: " + corruptingMarkerContent,
      corruptingMarkerContent.contains(corruptionReason)
    );
    assertTrue(
      "Corruption file must contain corruption cause [" + corruptionCauseMessage + "]: " + corruptingMarkerContent,
      corruptingMarkerContent.contains(corruptionCauseMessage)
    );
  }

  //==== more top-level tests

  @Test
  public void VFS_isRebuild_OnlyIf_ImplementationVersionChanged() throws Exception {
    //skip IN_MEMORY impl, since it is not really persistent
    //skip OVER_LOCK_FREE_FILE_CACHE impl if !LOCK_FREE_VFS_ENABLED (will fail)
    final List<RecordsStorageKind> allKinds = PageCacheUtils.LOCK_FREE_VFS_ENABLED ?
                                              List.of(REGULAR, OVER_LOCK_FREE_FILE_CACHE, OVER_MMAPPED_FILE) :
                                              List.of(REGULAR, OVER_MMAPPED_FILE);

    //check all combinations (from->to) of implementations:
    for (RecordsStorageKind kindBefore : allKinds) {
      for (RecordsStorageKind kindAfter : allKinds) {
        final Path cachesDir = temporaryDirectory.createDir();
        PersistentFSRecordsStorageFactory.setRecordsStorageImplementation(kindBefore);
        final FSRecordsImpl records = FSRecordsImpl.connect(
          cachesDir,
          new VfsLog(cachesDir.resolve("vfslog"), /*readOnly*/true)
        );

        final long firstVfsCreationTimestamp = records.getCreationTimestamp();

        records.dispose();
        Thread.sleep(500);//ensure system clock is moving

        //reopen:
        PersistentFSRecordsStorageFactory.setRecordsStorageImplementation(kindAfter);
        final FSRecordsImpl reopenedRecords = FSRecordsImpl.connect(
          cachesDir,
          new VfsLog(cachesDir.resolve("vfslog"), /*readOnly*/true), FSRecordsImpl.ON_ERROR_MARK_CORRUPTED_AND_SCHEDULE_REBUILD
        );
        final long reopenedVfsCreationTimestamp = reopenedRecords.getCreationTimestamp();


        if (kindBefore == kindAfter) {
          assertEquals(
            "VFS must _not_ be rebuild since storage version impl is not changed (" + kindBefore + " -> " + kindAfter + ")",
            firstVfsCreationTimestamp,
            reopenedVfsCreationTimestamp
          );
        }
        else {
          assertNotEquals(
            "VFS _must_ be rebuild from scratch since storage version impl is changed (" + kindBefore + " -> " + kindAfter + ")",
            firstVfsCreationTimestamp,
            reopenedVfsCreationTimestamp
          );
        }
      }
    }
  }



  //==== infrastructure:

  @After
  public void tearDown() throws Exception {
    PersistentFSRecordsStorageFactory.resetRecordsStorageImplementation();
  }

  private static void createRecords(final PersistentFSConnection connection,
                                    final int nRecords) throws IOException {
    final PersistentFSRecordsStorage records = connection.getRecords();
    for (int i = 0; i < nRecords; i++) {
      //Why .cleanRecord(): because PersistentFSSynchronizedRecordsStorage does not persist allocated
      // record if allocated record fields weren't modified. This is, generally, against
      // PersistentFSRecordsStorage contract, but (it seems) no use-sites are affected, and I
      // decided to not fix it, since it could affect performance for legacy implementation -- for nothing.
      //
      records.cleanRecord(records.allocateRecord());
    }
    connection.markDirty();
  }
}
