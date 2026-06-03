// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.ChildInfoImpl;
import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import com.intellij.platform.util.io.storages.StorageTestingUtils;
import com.intellij.util.io.CorruptedException;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.DataOutputStream;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FSRecordsImplTest {
  private FSRecordsImpl vfs;
  private Path vfsDir;
  private int previousRangeListThreshold = -1;

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

  @Test
  public void smallDirectoryChildren_areStoredAsExactListByDefault() throws Exception {
    int parentId = createDirectoryRecord("parent");
    int child1 = createFileRecord(parentId, "a.txt");
    int child2 = createFileRecord(parentId, "b.txt");

    saveChildren(parentId, true, child1, child2);

    int header = readChildrenHeader(parentId);
    assertEquals(2, header, "Small non-forced directory should keep the legacy exact-list children format");
    assertChildrenIds(vfs.list(parentId), child1, child2);
  }

  @Test
  public void directoryChildren_areStoredAndReadBackAsRangeList_ifForcedByThreshold() throws Exception {
    forceRangeListIfMoreChildrenThan(1);
    int parentId = createDirectoryRecord("parent");
    int child1 = createFileRecord(parentId, "a.txt");
    int child2 = createFileRecord(parentId, "b.txt");
    int child3 = createFileRecord(parentId, "c.txt");

    saveChildren(parentId, true, child1, child2, child3);

    int header = readChildrenHeader(parentId);
    assertTrue(header < 0, "Forced threshold should switch non-empty children storage to range-list format");
    assertChildrenIds(vfs.list(parentId), child1, child2, child3);
    assertTrue(readChildrenHeader(parentId) != 0, "Negative range-list header still means that the directory may have children");
  }

  @Test
  public void singletonDirectoryChild_canBeStoredAndReadBackAsRangeList_ifForcedByThreshold() throws Exception {
    forceRangeListIfMoreChildrenThan(1);
    int parentId = createDirectoryRecord("parent");
    int child = createFileRecord(parentId, "only.txt");

    saveChildren(parentId, true, child);

    int header = readChildrenHeader(parentId);
    assertEquals(-1, header, "A forced singleton range-list should contain exactly one range");
    assertChildrenIds(vfs.list(parentId), child);
  }

  @Test
  public void rangeListChildren_surviveVFSReinitialization() throws Exception {
    forceRangeListIfMoreChildrenThan(1);
    int parentId = createDirectoryRecord("parent");
    int child1 = createFileRecord(parentId, "a.txt");
    int child2 = createFileRecord(parentId, "b.txt");

    saveChildren(parentId, true, child1, child2);

    vfs = reloadVFS();

    assertChildrenIds(vfs.list(parentId), child1, child2);
  }

  @Test
  public void childrenCachedSemantics_doesNotDependOnRangeListFormat() throws Exception {
    forceRangeListIfMoreChildrenThan(1);
    int parentId = createDirectoryRecord("parent");
    int child = createFileRecord(parentId, "child.txt");

    saveChildren(parentId, false, child);

    ListResult notAllCached = vfs.list(parentId);
    assertFalse(notAllCached.allChildrenCached(), "Range-list format must not imply that all children are cached");
    assertChildrenIds(notAllCached, child);

    vfs.setFlags(parentId, vfs.getFlags(parentId) | PersistentFS.Flags.CHILDREN_CACHED);

    ListResult allCached = vfs.list(parentId);
    assertTrue(allCached.allChildrenCached(), "CHILDREN_CACHED flag alone should define cached semantics for range-list directories");
    assertChildrenIds(allCached, child);
  }

  @Test
  public void forEachChildOf_filtersForeignAndDeletedRecordsInsideRangeList() throws Exception {
    int parentId = createDirectoryRecord("parent");
    int ownChild1 = createFileRecord(parentId, "own-1.txt");
    int foreignParentId = createDirectoryRecord("foreign-parent");
    int foreignChild = createFileRecord(foreignParentId, "foreign.txt");
    int deletedChild = createFileRecord(parentId, "deleted.txt");
    int ownChild2 = createFileRecord(parentId, "own-2.txt");
    vfs.setFlags(deletedChild, vfs.getFlags(deletedChild) | PersistentFS.Flags.FREE_RECORD_FLAG);
    writeRangeListAttribute(parentId, ownChild1, ownChild2 + 1);

    List<Integer> visitedChildren = new ArrayList<>();
    boolean stoppedEarly = vfs.forEachChildOf(parentId, childId -> {
      visitedChildren.add(childId);
      return false;
    });

    assertFalse(stoppedEarly, "Consumer never requested early stop");
    assertArrayEquals(
      new int[]{ownChild1, ownChild2},
      visitedChildren.stream().mapToInt(Integer::intValue).toArray(),
      "Range-list scan should report only live records whose parentId matches the listed directory; foreign child was " + foreignChild
    );
  }

  @Test
  public void matchingRecordWithInvalidNameId_insideRangeList_isRejectedAsCorruption() throws Exception {
    int parentId = createDirectoryRecord("parent");
    int child = vfs.createRecord();
    vfs.connection().records().setParent(child, parentId);
    writeRangeListAttribute(parentId, child, child + 1);

    assertThrows(
      CorruptedException.class,
      () -> vfs.treeAccessor().doLoadChildren(parentId),
      "A live matching child without a valid nameId should make the range-list payload corrupted"
    );
  }

  @Test
  public void malformedRangeListStructures_areRejectedAsCorruption() throws Exception {
    int parentId = createDirectoryRecord("parent");
    int child = createFileRecord(parentId, "child.txt");

    assertCorruptedChildrenAttribute(parentId, output -> DataInputOutputUtil.writeINT(output, Integer.MIN_VALUE));
    assertCorruptedChildrenAttribute(parentId, output -> {
      DataInputOutputUtil.writeINT(output, -1);
      DataInputOutputUtil.writeINT(output, child - parentId);
    });
    assertCorruptedChildrenAttribute(parentId, output -> writeRangeListPayload(output, parentId, child, child));
    assertCorruptedChildrenAttribute(parentId, output -> writeRangeListPayload(output, parentId, child, child + 1, child + 1, child + 2));
    assertCorruptedChildrenAttribute(parentId, output -> writeRangeListPayload(output, parentId, child, child + 2));
  }

  @Test
  public void ordinaryAndRawTreeAccessors_returnSameChildrenForExactListAndRangeList() throws Exception {
    int exactParentId = createDirectoryRecord("exact-parent");
    int exactChild = createFileRecord(exactParentId, "exact.txt");
    saveChildren(exactParentId, true, exactChild);

    forceRangeListIfMoreChildrenThan(1);
    int rangeParentId = createDirectoryRecord("range-parent");
    int rangeChild1 = createFileRecord(rangeParentId, "range-1.txt");
    int rangeChild2 = createFileRecord(rangeParentId, "range-2.txt");
    saveChildren(rangeParentId, true, rangeChild1, rangeChild2);

    PersistentFSTreeAccessor ordinaryAccessor = new PersistentFSTreeAccessor(vfs.attributeAccessor(), vfs.recordAccessor(), vfs.connection());

    assertChildrenIds(ordinaryAccessor.doLoadChildren(exactParentId), exactChild);
    assertChildrenIds(ordinaryAccessor.doLoadChildren(rangeParentId), rangeChild1, rangeChild2);
    assertChildrenIds(vfs.list(exactParentId), exactChild);
    assertChildrenIds(vfs.list(rangeParentId), rangeChild1, rangeChild2);
  }


  /* ========================= infrastructure =========================================================================== */


  @BeforeEach
  void setUp(@TempDir Path vfsDir) {
    this.vfsDir = vfsDir;
    vfs = FSRecordsImpl.connect(vfsDir, FSRecordsImpl.ON_ERROR_RETHROW);
    previousRangeListThreshold = vfs.treeAccessor().setStoreChildrenAsRangesListIfMoreThan(Integer.MAX_VALUE);
  }

  @AfterEach
  void tearDown() throws Exception {
    if (vfs != null) {
      StorageTestingUtils.bestEffortToCloseAndClean(vfs);
    }
    if (previousRangeListThreshold > 0) {
      vfs.treeAccessor().setStoreChildrenAsRangesListIfMoreThan(previousRangeListThreshold);
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

  private void forceRangeListIfMoreChildrenThan(int threshold) {
    vfs.treeAccessor().setStoreChildrenAsRangesListIfMoreThan(threshold);
  }

  private int createDirectoryRecord(@NotNull String name) {
    return createRecord(PersistentFSRecordsStorage.NULL_ID, name, true);
  }

  private int createFileRecord(int parentId,
                               @NotNull String name) {
    return createRecord(parentId, name, false);
  }

  private int createRecord(int parentId,
                           @NotNull String name,
                           boolean directory) {
    int fileId = vfs.createRecord();
    vfs.updateRecordFields(fileId, parentId, attributes(directory), name, true);
    return fileId;
  }

  private static @NotNull FileAttributes attributes(boolean directory) {
    return new FileAttributes(directory, false, false, false, directory ? 0 : 1, 1, true);
  }

  private void saveChildren(int parentId,
                            boolean allCached,
                            int... childIds) throws IOException {
    List<ChildInfo> children = new ArrayList<>(childIds.length);
    for (int childId : childIds) {
      children.add(new ChildInfoImpl(childId, vfs.getNameIdByFileId(childId), null, null, null));
    }
    int parentModCount = vfs.getModCount(parentId);
    ListResult listResult = allCached ?
                            ListResult.allCached(parentModCount, children, parentId) :
                            ListResult.notAllCached(parentModCount, children, parentId);
    vfs.treeAccessor().doSaveChildren(parentId, listResult);
    if (allCached) {
      vfs.setFlags(parentId, vfs.getFlags(parentId) | PersistentFS.Flags.CHILDREN_CACHED);
    }
    else {
      vfs.setFlags(parentId, vfs.getFlags(parentId) & ~PersistentFS.Flags.CHILDREN_CACHED);
    }
  }

  private int readChildrenHeader(int parentId) throws IOException {
    try (DataInputStream input = vfs.attributeAccessor().readAttribute(parentId, PersistentFSTreeAccessor.CHILDREN_ATTR)) {
      assert input != null : "Children attribute must exist for parentId=" + parentId;
      return DataInputOutputUtil.readINT(input);
    }
  }

  private void writeRangeListAttribute(int parentId,
                                       int... boundaries) throws IOException {
    try (DataOutputStream output = vfs.attributeAccessor().writeAttribute(parentId, PersistentFSTreeAccessor.CHILDREN_ATTR)) {
      writeRangeListPayload(output, parentId, boundaries);
    }
  }

  private static void writeRangeListPayload(@NotNull DataOutputStream output,
                                            int parentId,
                                            int... boundaries) throws IOException {
    if (boundaries.length % 2 != 0) {
      throw new IllegalArgumentException("Range boundaries must come in min/maxExclusive pairs: " + Arrays.toString(boundaries));
    }
    DataInputOutputUtil.writeINT(output, -(boundaries.length / 2));
    int previousBoundary = parentId;
    for (int i = 0; i < boundaries.length; i += 2) {
      int minChildId = boundaries[i];
      int maxExclusiveChildId = boundaries[i + 1];
      DataInputOutputUtil.writeINT(output, minChildId - previousBoundary);
      DataInputOutputUtil.writeINT(output, maxExclusiveChildId - minChildId);
      previousBoundary = maxExclusiveChildId;
    }
  }

  private void assertCorruptedChildrenAttribute(int parentId,
                                                @NotNull ChildrenAttributeWriter writer) throws IOException {
    try (DataOutputStream output = vfs.attributeAccessor().writeAttribute(parentId, PersistentFSTreeAccessor.CHILDREN_ATTR)) {
      writer.write(output);
    }
    assertThrows(
      CorruptedException.class,
      () -> vfs.treeAccessor().doLoadChildren(parentId),
      "Malformed DIRECTORY_CHILDREN range-list payload should be reported as VFS corruption"
    );
  }

  private static void assertChildrenIds(@NotNull ListResult listResult,
                                        int... expectedChildIds) {
    assertArrayEquals(
      expectedChildIds,
      listResult.children.stream().mapToInt(ChildInfo::getId).toArray(),
      "Decoded children ids should match the records saved for the parent"
    );
  }

  @FunctionalInterface
  private interface ChildrenAttributeWriter {
    void write(@NotNull DataOutputStream output) throws IOException;
  }
}