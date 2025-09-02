// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.platform.util.io.storages.StorageTestingUtils;
import com.intellij.util.io.DataOutputStream;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FSRecordsImplTest {

  private FSRecordsImpl vfs;
  private Path vfsDir;

  @Test
  public void insertedRoots_CouldBeReadBack() throws Exception {
    int totalRoots = 100_000;               //too many roots could exceed attribute storage max record size
    int[] rootIds = new int[totalRoots];

    //shuffle roots to make sure rootUrlIds are not sequential:
    for (int i = 0; i < totalRoots; i++) {
      String rootUrl = "file:///root/" + (totalRoots - i - 1);
      vfs.connection().names().enumerate(rootUrl);
    }
    for (int i = 0; i < totalRoots; i++) {
      String rootUrl = "file:///root/" + i;
      rootIds[i] = vfs.findOrCreateRootRecord(rootUrl);
    }

    int[] rootIdsReadBack = vfs.listRoots();
    Arrays.sort(rootIds);
    Arrays.sort(rootIdsReadBack);
    assertArrayEquals(
      rootIds,
      rootIdsReadBack,
      "rootIds stored must be equal to rootIds read back"
    );
  }

  @Test
  public void insertedRoots_CouldBeReadBack_AfterVFSReinitialization() throws Exception {
    int totalRoots = 100_000;               //too many roots could exceed attribute storage max record size
    int[] rootIds = new int[totalRoots];
    //shuffle roots to make sure rootUrlIds are not sequential:
    for (int i = 0; i < totalRoots; i++) {
      String rootUrl = "file:///root/" + (totalRoots - i - 1);
      vfs.connection().names().enumerate(rootUrl);
    }
    for (int i = 0; i < totalRoots; i++) {
      String rootUrl = "file:///root/" + i;
      rootIds[i] = vfs.findOrCreateRootRecord(rootUrl);
    }

    vfs = reloadVFS();

    int[] rootIdsReadBack = vfs.listRoots();
    Arrays.sort(rootIds);
    Arrays.sort(rootIdsReadBack);
    assertArrayEquals(
      rootIds,
      rootIdsReadBack,
      "rootIds stored must be equal to rootIds read back even after VFS was re-initialized"
    );
  }

  @Test
  public void insertedRoots_CouldBeResolvedByUrl_AfterVFSReinitialization() throws Exception {
    int totalRoots = 100_000;               //too many roots could exceed attribute storage max record size
    int[] rootIds = new int[totalRoots];
    //shuffle roots to make sure rootUrlIds are not sequential:
    for (int i = 0; i < totalRoots; i++) {
      String rootUrl = "file:///root/" + (totalRoots - i - 1);
      vfs.connection().names().enumerate(rootUrl);
    }
    for (int i = 0; i < totalRoots; i++) {
      String rootUrl = "file:///root/" + i;
      rootIds[i] = vfs.findOrCreateRootRecord(rootUrl);
    }

    vfs = reloadVFS();

    for (int i = 0; i < totalRoots; i++) {
      String rootUrl = "file:///root/" + i;
      int rootId = vfs.findOrCreateRootRecord(rootUrl);
      assertEquals(
        rootIds[i],
        rootId,
        "root[" + i + "](url:"+rootUrl+") must be resolved to rootId: #" + rootIds[i] + ", but got #" + rootId + " instead"
      );
    }
  }


  @Test
  public void writtenFileContentIsDeduplicated_onlyUniqueContentsAreStored() throws IOException {
    int contentsToInsert = 1024;
    int uniqueContentsCount = 4;
    for (int i = 0; i < contentsToInsert; i++) {
      int fileId = vfs.createRecord();
      String content = "testContent_" + (i % uniqueContentsCount);
      try (DataOutputStream stream = vfs.writeContent(fileId, /*fixed: */false)) {
        stream.writeUTF(content);
      }
    }

    assertEquals(
      uniqueContentsCount,
      vfs.connection().contents().getRecordsCount(),
      "Only " + uniqueContentsCount + " unique contents should be stored"
    );
  }

  @Test
  public void writtenFileContent_couldBeReadBackAsIs() throws IOException {
    int contentsToInsert = 1024;
    int uniqueContentsCount = 4;

    int[] fileIds = new int[contentsToInsert];
    for (int i = 0; i < contentsToInsert; i++) {
      int fileId = vfs.createRecord();
      String content = "testContent_" + (i % uniqueContentsCount);
      try (DataOutputStream stream = vfs.writeContent(fileId, /*fixed: */false)) {
        stream.writeUTF(content);
      }
      fileIds[i] = fileId;
    }

    //read back:
    for (int i = 0; i < contentsToInsert; i++) {
      int fileId = fileIds[i];
      String expectedContent = "testContent_" + (i % uniqueContentsCount);
      try (DataInputStream stream = new DataInputStream(vfs.readContent(fileId))) {
        String actualContent = stream.readUTF();
        assertEquals(
          expectedContent,
          actualContent,
          "[i:" + i + "][fileId:" + fileId + "]: written content must be read back"
        );
      }
    }
  }

  @Test
  public void fileRecordModCountChanges_onlyIfFileContentActuallyChanges() throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    String[] randomContents = Stream.generate(
        () -> StorageTestingUtils.randomString(rnd, rnd.nextInt(0, 1024))
      )
      .limit(1024)
      .toArray(String[]::new);

    int fileId = vfs.createRecord();

    String previousContent = "";
    writeContent(fileId, previousContent);
    for (int attempt = 0; attempt < 1024; attempt++) {
      int modCountBefore = vfs.getModCount(fileId);
      String content = randomContents[rnd.nextInt(randomContents.length)];
      writeContent(fileId, content);
      int modCountAfter = vfs.getModCount(fileId);

      if (previousContent.equals(content)) {
        assertEquals(
          modCountAfter, modCountBefore,
          "[" + previousContent + "]->[" + content + "]:" +
          " modCountAfter(" + modCountAfter + ") most be == modCountBefore(" + modCountBefore + ") since content doesn't change"
        );
      }
      else {
        //'+1' is a bit of over-specification: modCount 'after' must be more than 'before' -- but it doesn't really
        // matter how much more. But I also don't want to grow modCount without a reason, so I check the minimum
        // increment:
        assertEquals(
          modCountAfter, modCountBefore + 1,
          "[" + previousContent + "]->[" + content + "]:" +
          "modCountAfter(" + modCountAfter + ") most be modCountBefore(" + modCountBefore + ")+1 since content does change"
        );
      }
      previousContent = content;
    }
  }

  @Test
  public void fileRecordModCountChanges_ifFileAttributeWritten_regardlessOfActualValueChange() throws IOException {
    FileAttribute fileAttribute = new FileAttribute("X");
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    String[] randomAttributeValues = Stream.generate(
        () -> StorageTestingUtils.randomString(rnd, rnd.nextInt(0, 128))
      )
      .limit(1024)
      .toArray(String[]::new);

    int fileId = vfs.createRecord();

    String previousAttributeValue = "";
    try (DataOutputStream stream = vfs.writeAttribute(fileId, fileAttribute)) {
      stream.writeUTF(previousAttributeValue);
    }
    for (int attempt = 0; attempt < 1024; attempt++) {
      int modCountBefore = vfs.getModCount(fileId);
      String attributeValue = randomAttributeValues[rnd.nextInt(randomAttributeValues.length)];
      try (DataOutputStream stream = vfs.writeAttribute(fileId, fileAttribute)) {
        stream.writeUTF(attributeValue);
      }
      int modCountAfter = vfs.getModCount(fileId);

      //'+1' is a bit of over-specification: modCount 'after' must be more than 'before' -- but it doesn't really
      // matter how much more. But I also don't want to grow modCount without a reason, so I check the minimum
      // increment:
      assertEquals(
        modCountAfter, modCountBefore + 1,
        "[" + previousAttributeValue + "]->[" + attributeValue + "]:" +
        "modCountAfter(" + modCountAfter + ") most be modCountBefore(" + modCountBefore + ")+1"
      );
      previousAttributeValue = attributeValue;
    }
  }


  /* ========================= infrastructure =========================================================================== */


  @BeforeEach
  void setUp(@TempDir Path vfsDir) {
    this.vfsDir = vfsDir;
    vfs = FSRecordsImpl.connect(vfsDir, FSRecordsImpl.ON_ERROR_RETHROW);
  }

  @AfterEach
  void tearDown() throws Exception {
    if (vfs != null) {
      StorageTestingUtils.bestEffortToCloseAndClean(vfs);
    }
  }

  private FSRecordsImpl reloadVFS() throws Exception {
    StorageTestingUtils.bestEffortToCloseAndUnmap(vfs);
    return FSRecordsImpl.connect(vfsDir, FSRecordsImpl.ON_ERROR_RETHROW);
  }

  private void writeContent(int fileId,
                            @NotNull String content) {
    vfs.writeContent(fileId, new ByteArraySequence(content.getBytes(UTF_8)), true);
  }
}