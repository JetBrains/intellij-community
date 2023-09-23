// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.persistent.recovery.ContentStoragesRecoverer;
import com.intellij.testFramework.TemporaryDirectory;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests VFS ability to recover from various corruptions
 */
public class VFSCorruptionRecoveryTest {

  public static final FileAttribute TEST_FILE_ATTRIBUTE = new FileAttribute("VFSCorruptionRecoveryTest.TEST_ATTRIBUTE");

  @Rule
  public final TemporaryDirectory temporaryDirectory = new TemporaryDirectory();

  /**
   * All the data files VFS contains, which is worth to mess up for testing corruptions
   * (i.e. some files could be excluded if they aren't worth the efforts)
   */
  private List<String> vfsCrucialDataFilesNames;

  @Before
  public void setup() throws Exception {
    Path cachesDir = temporaryDirectory.createDir();

    setupVFSFillSomeDataAndClose(cachesDir);
    vfsCrucialDataFilesNames = Files.list(cachesDir)
      .filter(path -> Files.isRegularFile(path))
      //ResizableMappedFile _is_ able to recover .len file, so don't waste time deleting it
      .filter(path -> !path.getFileName().toString().endsWith(".len"))
      //DurableEnumerator recovers hashToId mapping from valuesLog content:
      .filter(path -> !path.getFileName().toString().endsWith(".hashToId"))
      .sorted()
      .map(path -> path.getFileName().toString())
      .toList();
  }

  @Test
  public void VFS_init_WithoutRecoverers_Fails_If_AnyStorageFileRemoved() throws Exception {
    //We want to verify that initialization quick-checks are able to detect corruptions.
    // The verification is very rough: just remove one of the data files and see if VFS
    // init fails. Missed data file is by no means a typical corruption, but it helps at
    // least verify the main quick-check logic.
    // Ideally, we should introduce random corruptions to data files, and see if VFS init
    // is able to detect _that_ -- but this is a much larger task, especially since we know
    // quick-checks are really not 100% sensitive -- they could miss some corruptions.
    // So full-scale sampling of quick-checks sensitivity to various kinds of corruptions
    // -- is a dedicated task, while here we do something that fits in a unit-test:


    List<String> filesNotLeadingToVFSRebuild = new ArrayList<>();
    int vfsFilesCount = vfsCrucialDataFilesNames.size();
    for (int i = 0; i < vfsFilesCount; i++) {
      String dataFileNameToDelete = vfsCrucialDataFilesNames.get(i);

      Path cachesDir = temporaryDirectory.createDir();

      setupVFSFillSomeDataAndClose(cachesDir);

      Path fileToDelete = Files.list(cachesDir)
        .filter(path -> Files.isRegularFile(path))
        .filter(path -> path.getFileName().toString().equals(dataFileNameToDelete))
        .findFirst().orElse(null);
      if (fileToDelete == null) {
        fail("Can't delete[" + dataFileNameToDelete + "] -- there is no such file amongst \n" + Files.list(cachesDir).toList());
      }

      FileUtil.delete(fileToDelete);

      try {
        //try reopen:
        PersistentFSConnector.tryInit(
          cachesDir,
          FSRecordsImpl.currentImplementationVersion(),
          false,
          Collections.emptyList(),
          Collections.emptyList()
        );
        filesNotLeadingToVFSRebuild.add(fileToDelete.getFileName().toString());
      }
      catch (IOException ex) {
        if (ex instanceof VFSInitException vfsLoadEx) {
          System.out.println(fileToDelete.getFileName().toString() + " removed -> " + vfsLoadEx.category());
        }
      }
    }

    assertTrue(
      "VFS is not rebuilt if one of " + filesNotLeadingToVFSRebuild + " is deleted",
      filesNotLeadingToVFSRebuild.isEmpty()
    );
  }

  @Test
  public void VFS_init_WithContentStoragesRecoverer_DoesntFail_If_ContentHashesStorageFilesRemoved() throws Exception {
    List<String> contentHashesDataFilesNames = vfsCrucialDataFilesNames.stream().filter(name -> name.contains("contentHashes")).toList();
    for (int i = 0; i < contentHashesDataFilesNames.size(); i++) {
      String contentHashesDataFileNameToDelete = contentHashesDataFilesNames.get(i);

      Path cachesDir = temporaryDirectory.createDir();
      setupVFSFillSomeDataAndClose(cachesDir);

      Path contentHashesFileToDelete = Files.list(cachesDir)
        .filter(path -> Files.isRegularFile(path))
        .filter(path -> path.getFileName().toString().equals(contentHashesDataFileNameToDelete))
        .findFirst().orElse(null);
      if (contentHashesFileToDelete == null) {
        fail(
          "Can't delete[" + contentHashesDataFileNameToDelete + "] -- there is no such file amongst \n" + Files.list(cachesDir).toList());
      }

      FileUtil.delete(contentHashesFileToDelete);

      //reopen:
      PersistentFSConnector.tryInit(
        cachesDir,
        FSRecordsImpl.currentImplementationVersion(),
        false,
        Collections.emptyList(),
        List.of(new ContentStoragesRecoverer())
      );
    }
  }


  @Test
  public void VFS_init_WithContentStoragesRecoverer_DoesntFail_If_ContentStorageFilesRemoved() throws Exception {
    List<String> contentStorageDataFilesNames = vfsCrucialDataFilesNames.stream().filter(name -> name.contains("content.dat")).toList();
    for (int i = 0; i < contentStorageDataFilesNames.size(); i++) {
      String contentStorageDataFileNameToDelete = contentStorageDataFilesNames.get(i);

      Path cachesDir = temporaryDirectory.createDir();
      setupVFSFillSomeDataAndClose(cachesDir);

      Path contentHashesFileToDelete = Files.list(cachesDir)
        .filter(path -> Files.isRegularFile(path))
        .filter(path -> path.getFileName().toString().equals(contentStorageDataFileNameToDelete))
        .findFirst().orElse(null);
      if (contentHashesFileToDelete == null) {
        fail(
          "Can't delete[" + contentStorageDataFileNameToDelete + "] -- there is no such file amongst \n" + Files.list(cachesDir).toList());
      }

      FileUtil.delete(contentHashesFileToDelete);

      //reopen:
      PersistentFSConnection connection = PersistentFSConnector.tryInit(
        cachesDir,
        FSRecordsImpl.currentImplementationVersion(),
        false,
        Collections.emptyList(),
        List.of(new ContentStoragesRecoverer())
      );
      assertTrue(
        "contentIds must be invalidated since ContentStorage was cleared",
        connection.recoveryInfo().invalidateContentIds
      );
    }
  }

  //================ infrastructure: ================================================================


  private static void setupVFSFillSomeDataAndClose(Path cachesDir) throws IOException {
    FSRecordsImpl fsRecords = FSRecordsImpl.connect(cachesDir);

    //add something to VFS so it is not empty
    int testFileId = fsRecords.createRecord();
    fsRecords.setName(testFileId, "test", PersistentFSRecordsStorage.NULL_ID);
    try (var stream = fsRecords.writeContent(testFileId, false)) {
      stream.writeUTF("test");
    }
    try (var stream = fsRecords.writeAttribute(testFileId, TEST_FILE_ATTRIBUTE)) {
      stream.writeInt(42);
    }

    fsRecords.dispose();
  }

  @After
  public void tearDown() throws Exception {
  }
}
