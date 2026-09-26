// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.util.io.DataInputOutputUtil;
import it.unimi.dsi.fastutil.ints.IntSets;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Exercises huge-directory children storage through regular VFS event application ({@linkplain ManagingFS}),
 * not by writing FSRecords directly.
 */
@TestApplication
public class PersistentFSHugeDirectoriesIntegrationTest {
  private static final int TOTAL_FILES = 350_000;
  private static final int BATCH_SIZE = 10_000;
  private static final int RANGE_LIST_THRESHOLD = 300_000;

  private static int previousRangeListThreshold = -1;

  @BeforeAll
  static void setUp() {
    previousRangeListThreshold = FSRecords.getInstance().treeAccessor().setStoreChildrenAsRangesListIfMoreThan(RANGE_LIST_THRESHOLD);
  }

  @AfterAll
  static void tearDown() {
    if (previousRangeListThreshold >= 0) {
      FSRecords.getInstance().treeAccessor().setStoreChildrenAsRangesListIfMoreThan(previousRangeListThreshold);
    }
  }

  @Test
  public void hugeDirectoriesRemainIterableAfterBatchCreateAndBatchMoveAcrossStorageFormats() throws Exception {
    TempFileSystem fs = TempFileSystem.getInstance();
    VirtualFile root = ManagingFS.getInstance().findRoot("/", fs);
    assertNotNull(root, "TempFileSystem root must be registered in VFS");

    VirtualFile testRoot = createDirectory(root, "huge-directories-" + Long.toUnsignedString(System.nanoTime()));
    try {
      VirtualFile source = createDirectory(testRoot, "source");
      VirtualFile target = createDirectory(testRoot, "target");

      assertChildrenIterable(source, /*expectedChildren: */0);
      assertChildrenIterable(target, /*expectedChildren: */0);

      for (int created = 0; created < TOTAL_FILES; created += BATCH_SIZE) {
        createFilesBatchThroughEvents(fs, (VirtualDirectoryImpl)source, created);

        int sourceCount = created + BATCH_SIZE;
        assertChildrenIterable(source, sourceCount);
        if (sourceCount == BATCH_SIZE) {
          assertTrue(readChildrenHeader(source) > 0, "The source directory should start in exact-list format");
        }
      }

      assertTrue(readChildrenHeader(source) < 0, "The source directory should switch to range-list format after growing huge");
      assertBatchMoveKeepsDirectoriesIterable(fs, source, target);
    }
    finally {
      deleteIfValid(testRoot);
    }
  }

  @Test
  public void hugeDirectoriesRemainIterableWhenCreatedFilesAreDiscoveredByRefresh() throws Exception {
    TempFileSystem fs = TempFileSystem.getInstance();
    VirtualFile root = ManagingFS.getInstance().findRoot("/", fs);
    assertNotNull(root, "TempFileSystem root must be registered in VFS");

    VirtualFile testRoot = createDirectory(root, "huge-directories-refresh-" + Long.toUnsignedString(System.nanoTime()));
    try {
      VirtualFile source = createDirectory(testRoot, "source");
      VirtualFile target = createDirectory(testRoot, "target");

      assertChildrenIterable(source, /*expectedChildren: */0);
      assertChildrenIterable(target, /*expectedChildren: */0);

      for (int created = 0; created < TOTAL_FILES; created += BATCH_SIZE) {
        createFilesBatchInBackingFs(fs, source, created);
        source.refresh(/*async: */ false, /*recursive: */ false);

        int sourceCount = created + BATCH_SIZE;
        assertChildrenIterable(source, sourceCount);

        int header = readChildrenHeader(source);
        if (sourceCount == BATCH_SIZE) {
          assertTrue(header > 0, "The source directory should start in exact-list format");
        }
      }

      assertTrue(readChildrenHeader(source) < 0, "The source directory should switch to range-list format after growing huge");
      assertBatchMoveKeepsDirectoriesIterable(fs, source, target);
    }
    finally {
      deleteIfValid(testRoot);
    }
  }

  private static @NotNull VirtualFile createDirectory(@NotNull VirtualFile parent,
                                                      @NotNull String name) throws IOException {
    return WriteAction.computeAndWait(() -> parent.createChildDirectory(PersistentFSHugeDirectoriesIntegrationTest.class, name));
  }

  private void createFilesBatchThroughEvents(@NotNull TempFileSystem fs,
                                             @NotNull VirtualDirectoryImpl parent,
                                             int firstFileIndex) throws IOException {
    List<VFileEvent> events = new ArrayList<>(BATCH_SIZE);
    List<CharSequence> createdNames = new ArrayList<>(BATCH_SIZE);
    for (int i = firstFileIndex; i < firstFileIndex + BATCH_SIZE; i++) {
      String name = fileName(i);
      fs.createIfNotExists(parent, name);
      events.add(new VFileCreateEvent(this, parent, name, false, null, null, null));
      createdNames.add(name);
    }
    parent.removeChildren(IntSets.emptySet(), createdNames);
    processEvents(events);
  }

  private static void createFilesBatchInBackingFs(@NotNull TempFileSystem fs,
                                                  @NotNull VirtualFile parent,
                                                  int firstFileIndex) throws IOException {
    for (int i = firstFileIndex; i < firstFileIndex + BATCH_SIZE; i++) {
      fs.createIfNotExists(parent, fileName(i));
    }
  }

  private void assertBatchMoveKeepsDirectoriesIterable(@NotNull TempFileSystem fs,
                                                       @NotNull VirtualFile source,
                                                       @NotNull VirtualFile target) throws IOException {
    assertChildrenIterable(target, /*expectedChildren: */0);

    VirtualFile[] filesToMove = source.getChildren();
    assertEquals(TOTAL_FILES, filesToMove.length);

    for (int moved = 0; moved < TOTAL_FILES; moved += BATCH_SIZE) {
      moveFilesBatchInBackingFs(fs, filesToMove, moved, target);
      source.refresh(/*async: */ false, /*recursive: */ false);
      target.refresh(/*async: */ false, /*recursive: */ false);

      int sourceCount = TOTAL_FILES - moved - BATCH_SIZE;
      int targetCount = moved + BATCH_SIZE;
      assertChildrenIterable(source, sourceCount);
      assertChildrenIterable(target, targetCount);

      int targetHeader = readChildrenHeader(target);
      if (targetCount == BATCH_SIZE) {
        assertTrue(targetHeader > 0, "The target directory should start in exact-list format");
      }
    }

    assertTrue(readChildrenHeader(target) < 0, "The target directory should switch to range-list format after receiving all files");
    assertChildrenIterable(source, 0);
    assertChildrenIterable(target, TOTAL_FILES);
  }

  private void moveFilesBatchInBackingFs(@NotNull TempFileSystem fs,
                                         VirtualFile @NotNull [] filesToMove,
                                         int firstFileIndex,
                                         @NotNull VirtualFile target) throws IOException {
    for (int i = firstFileIndex; i < firstFileIndex + BATCH_SIZE; i++) {
      fs.moveFile(this, filesToMove[i], target);
    }
  }

  private static void processEvents(@NotNull List<? extends VFileEvent> events) {
    WriteAction.runAndWait(() -> RefreshQueue.getInstance().processEvents(false, events));
  }

  private static void assertChildrenIterable(@NotNull VirtualFile directory,
                                             int expectedChildrenCount) {
    int childrenCount = 0;
    for (VirtualFile child : directory.getChildren()) {
      if (!child.isValid()) {
        fail("Invalid child while iterating " + directory + ": " + child);
      }
      assertSame(directory, child.getParent(), "Child parent should match the directory being iterated");
      childrenCount++;
    }
    assertEquals(expectedChildrenCount, childrenCount, "Unexpected children count while iterating " + directory);
  }

  private static int readChildrenHeader(@NotNull VirtualFile directory) throws IOException {
    try (DataInputStream input = FSRecords.getInstance().attributeAccessor()
      .readAttribute(fileId(directory), PersistentFSTreeAccessor.CHILDREN_ATTR)) {
      return input == null ? 0 : DataInputOutputUtil.readINT(input);
    }
  }

  private static int fileId(@NotNull VirtualFile file) {
    return ((VirtualFileWithId)file).getId();
  }

  private static @NotNull String fileName(int index) {
    return "file-" + index + ".txt";
  }

  private static void deleteIfValid(@NotNull VirtualFile file) throws IOException {
    if (file.isValid()) {
      WriteAction.runAndWait(() -> file.delete(PersistentFSHugeDirectoriesIntegrationTest.class));
    }
  }
}
