/*
 * Copyright 2004-2020 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.jetbrains.mvstore;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.integratedBinaryPacking.IntBitPacker;
import org.jetbrains.integratedBinaryPacking.LongBitPacker;

import java.nio.ByteBuffer;
import java.util.Arrays;

class NonLeafPage<K, V> extends Page<K, V> {
  // child page info (long) + child page count (var long)
  private static final int CHILD_METADATA_SIZE = Long.BYTES + DataUtil.VAR_LONG_MAX_SIZE;

  /**
   * The child page references.
   */
  private PageReference<K, V>[] children;

  /**
   * The total entry count of this page and all children.
   */
  private long totalCount;

  NonLeafPage(MVMap<K, V> map, ByteBuf buf, long info, int chunkId) {
    super(map, info,  buf, chunkId);
  }

  NonLeafPage(@NotNull MVMap<K, V> map, @NotNull KeyManager<K> keyManager, int memory, PageReference<K, V>[] children, long totalCount) {
    super(map, keyManager, memory);

    this.children = children;
    this.totalCount = totalCount;
    if (MVStore.ASSERT_MODE) {
      assert (children.length - keyManager.getKeyCount()) == 1;
      assert totalCount == calculateTotalCount();
    }
  }

  @Override
  public int getNodeType() {
    return PAGE_TYPE_NODE;
  }

  @Override
  public Page<K, V> copy(MVMap<K, V> map) {
    return new IncompleteNonLeaf<>(map, this);
  }

  @Override
  public Page<K, V> getChildPage(int index) {
    PageReference<K, V> ref = children[index];
    Page<K, V> page = ref.getPage();
    if (page == null) {
      page = map.getStore().readPage(map, ref.getPos());
      assert ref.getPos() == page.getPosition();
      assert ref.totalCount == page.getTotalCount();
    }
    return page;
  }

  @Override
  public long getChildPagePos(int index) {
    return children[index].getPos();
  }

  @Override
  public V getValue(int index) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Page<K, V> split(int at, boolean left) {
    KeyManager<K> newKeys = left ? keyManager.copy(0, at, map) : keyManager.copy(at + 1, keyManager.getKeyCount(), map);
    PageReference<K, V>[] newChildren = createRefStorage(newKeys.getKeyCount() + 1);
    System.arraycopy(children, left ? 0 : (at + 1), newChildren, 0, newChildren.length);

    long totalCount = 0;
    for (PageReference<K, V> child : newChildren) {
      totalCount += child.totalCount;
    }
    return new NonLeafPage<>(map, newKeys, calculateSerializedDataSize(newChildren.length), newChildren, totalCount);
  }

  @Override
  public long getTotalCount() {
    if (MVStore.ASSERT_MODE) {
      assert !isComplete() || totalCount == calculateTotalCount() : "Total count: " + totalCount + " != " + calculateTotalCount();
    }
    return totalCount;
  }

  private long calculateTotalCount() {
    long result = 0;
    for (PageReference<K, V> child : children) {
      result += child.totalCount;
    }
    return result;
  }

  void recalculateTotalCount() {
    totalCount = calculateTotalCount();
  }

  @Override
  long getCounts(int index) {
    return children[index].totalCount;
  }

  void setChild(int index, Page<K, V> c) {
    assert c != null;
    PageReference<K, V> child = children[index];
    if (c != child.getPage() || c.getPosition() != child.getPos()) {
      totalCount += c.getTotalCount() - child.totalCount;
      children = children.clone();
      children[index] = new PageReference<>(c);
    }
  }

  static <K, V> NonLeafPage<K, V> replaceChild(NonLeafPage<K, V> source, int index, Page<K, V> child) {
    PageReference<K, V> oldChildRef = source.children[index];

    PageReference<K, V>[] newChildren = source.children;
    if (child != oldChildRef.getPage() || child.getPosition() != oldChildRef.getPos()) {
      newChildren = newChildren.clone();
      newChildren[index] = new PageReference<>(child);
    }

    // memory is not changed - it's our own memory
    return new NonLeafPage<>(source.map, source.keyManager, source.memory, newChildren, (source.totalCount - oldChildRef.totalCount) + child.getTotalCount());
  }

  static <K, V> NonLeafPage<K, V> replaceSplitChild(NonLeafPage<K, V> parentPage,
                                                    int index,
                                                    K key,
                                                    Page<K, V> leftChildPage,
                                                    Page<K, V> rightChildPage) {
    PageReference<K, V> oldChildRef = parentPage.children[index];

    PageReference<K, V>[] newChildren = createRefStorage(parentPage.children.length + 1);
    DataUtil.copyWithGap(parentPage.children, newChildren, parentPage.children.length, index);
    newChildren[index] = new PageReference<>(leftChildPage);
    newChildren[index + 1] = new PageReference<>(rightChildPage);

    long newTotalCount = (parentPage.totalCount - oldChildRef.totalCount) + leftChildPage.getTotalCount() + rightChildPage.getTotalCount();
    MVMap<K, V> map = parentPage.map;
    KeyManager<K> newKeyManager = parentPage.keyManager.insertKey(index, key, map);
    return new NonLeafPage<>(map, newKeyManager, parentPage.memory + CHILD_METADATA_SIZE, newChildren, newTotalCount);
  }

  @Override
  public Page<K, V> remove(int index) {
    int currentChildCount = getRawChildPageCount();
    PageReference<K, V>[] newChildren = createRefStorage(currentChildCount - 1);
    DataUtil.copyExcept(children, newChildren, currentChildCount, index);
    KeyManager<K> newKeyManager = keyManager.remove(index == keyManager.getKeyCount() ? (index - 1) : index, map);
    return new NonLeafPage<>(map, newKeyManager, memory - CHILD_METADATA_SIZE, newChildren, totalCount - children[index].totalCount);
  }

  @Override
  public int removeAllRecursive(long version) {
    int unsavedMemory = removePage(version);
    if (isPersistent()) {
      for (int i = 0, size = map.getChildPageCount(this); i < size; i++) {
        PageReference<K, V> ref = children[i];
        Page<K, V> page = ref.getPage();
        if (page != null) {
          unsavedMemory += page.removeAllRecursive(version);
        }
        else {
          long pagePos = ref.getPos();
          assert DataUtil.isPageSaved(pagePos);
          if (DataUtil.isLeafPage(pagePos)) {
            map.getStore().accountForRemovedPage(pagePos, version, map.isSingleWriter(), -1);
          }
          else {
            unsavedMemory += map.getStore().readPage(map, pagePos).removeAllRecursive(version);
          }
        }
      }
    }
    return unsavedMemory;
  }

  @Override
  public CursorPos<K, V> getPrependCursorPos(CursorPos<K, V> cursorPos) {
    Page<K, V> childPage = getChildPage(0);
    return childPage.getPrependCursorPos(new CursorPos<>(this, 0, cursorPos));
  }

  @Override
  public CursorPos<K, V> getAppendCursorPos(CursorPos<K, V> cursorPos) {
    int keyCount = getKeyCount();
    Page<K, V> childPage = getChildPage(keyCount);
    return childPage.getAppendCursorPos(new CursorPos<>(this, keyCount, cursorPos));
  }

  @Override
  protected void writePayload(ByteBuf buf, int keyCount) {
    int dataStart = buf.writerIndex();
    int typePosition = dataStart - 1;

    for (int i = 0; i <= keyCount; i++) {
      PageReference<K, V> child = children[i];
      long info = child.getPos();
      buf.writeLong(info);
      if (info != 0) {
        LongBitPacker.writeVar(buf, child.totalCount);
      }
    }

    keyManager.write(keyCount, map, buf);

    if (!map.getKeyType().isGenericCompressionApplicable()) {
      return;
    }

    @SuppressWarnings("DuplicatedCode")
    int uncompressedLength = buf.writerIndex() - dataStart;
    // some compression threshold
    if (uncompressedLength > 256) {
      MVStore store = map.getStore();
      int compressionLevel = store.getCompressionLevel();
      if (compressionLevel > 0) {
        compressData(buf, PAGE_TYPE_NODE, typePosition, dataStart, store, uncompressedLength, compressionLevel);
      }
    }
  }

  @Override
  protected KeyManager<K> readPayload(ByteBuf buf, int keyCount, boolean isCompressed, int pageLength, int pageStartReaderIndex) {
    children = createRefStorage(keyCount + 1);

    if (!isCompressed) {
      return doReadPayload(buf, keyCount);
    }

    @SuppressWarnings("DuplicatedCode")
    int sizeDiff = IntBitPacker.readVar(buf);
    int compressedStart = buf.readerIndex();
    int compressedLength = pageLength - (compressedStart - pageStartReaderIndex);
    int decompressedLength = compressedLength + sizeDiff;
    ByteBuf uncompressed = PooledByteBufAllocator.DEFAULT.buffer(decompressedLength, decompressedLength);
    try {
      ByteBuffer inNioBuf = DataUtil.getNioBuffer(buf, compressedStart, compressedLength);
      map.getStore().getDecompressor().decompress(inNioBuf, uncompressed.internalNioBuffer(0, decompressedLength));
      uncompressed.writerIndex(decompressedLength);

      KeyManager<K> keyManager = doReadPayload(uncompressed, keyCount);
      buf.readerIndex(compressedStart + compressedLength);
      return keyManager;
    }
    finally {
      uncompressed.release();
    }
  }

  private KeyManager<K> doReadPayload(ByteBuf buf, int keyCount) {
    long total = 0;
    for (int i = 0; i <= keyCount; i++) {
      long info = buf.readLong();
      if (info == 0) {
        children[i] = PageReference.empty();
      }
      else {
        long childTotalCount = LongBitPacker.readVar(buf);
        children[i] = new PageReference<>(info, childTotalCount);
        total += childTotalCount;
      }
    }
    totalCount = total;
    return map.getKeyType().createManager(buf, keyCount);
  }

  @Override
  protected int calculateMemory() {
    return calculateSerializedDataSize(getRawChildPageCount());
  }

  // without keys
  static int calculateSerializedDataSize(int childCount) {
    return Page.PAGE_MEMORY + (childCount * CHILD_METADATA_SIZE);
  }

  @Override
  void writeUnsavedRecursive(Chunk chunk, ByteBuf buf, LongArrayList toc) {
    if (!isSaved()) {
      writeChildrenRecursive(chunk, buf, toc);
      // write after children because to write we need to know about children page info - it maybe changed after children write
      // not possible to patch data in buffer because page maybe compressed
      write(chunk, buf, toc);
    }
  }

  void writeChildrenRecursive(Chunk chunk, ByteBuf buf, LongArrayList toc) {
    int len = getRawChildPageCount();
    for (int i = 0; i < len; i++) {
      PageReference<K, V> ref = children[i];
      Page<K, V> p = ref.getPage();
      if (p != null) {
        p.writeUnsavedRecursive(chunk, buf, toc);
        ref.resetPos();
      }
    }
  }

  @Override
  public int getUnsavedMemory() {
    if (isSaved()) {
      return 0;
    }

    int result = memory;
    for (PageReference<K, V> childRef : children) {
      Page<K, V> childPage = childRef.getPage();
      if (childPage != null) {
        result += childPage.getUnsavedMemory();
      }
    }
    return result;
  }

  @Override
  void releaseSavedPages() {
    int len = getRawChildPageCount();
    for (int i = 0; i < len; i++) {
      children[i].clearPageReference();
    }
  }

  @Override
  public int getRawChildPageCount() {
    return keyManager.getKeyCount() + 1;
  }

  @Override
  public void dump(StringBuilder buff) {
    super.dump(buff);
    int keyCount = getKeyCount();
    for (int i = 0; i <= keyCount; i++) {
      if (i > 0) {
        buff.append(" ");
      }
      buff.append('[').append(Long.toHexString(children[i].getPos())).append(']');
      if (i < keyCount) {
        buff.append(' ').append(keyManager.getKey(i));
      }
    }
  }

  private static final class IncompleteNonLeaf<K, V> extends NonLeafPage<K, V> {
    private boolean complete;

    private IncompleteNonLeaf(MVMap<K, V> map, NonLeafPage<K, V> source) {
      super(map, source.keyManager, source.memory, constructEmptyPageRefs(source.getRawChildPageCount()), source.getTotalCount());
    }

    private static <K, V> PageReference<K, V>[] constructEmptyPageRefs(int size) {
      // replace child pages with empty pages
      PageReference<K, V>[] children = createRefStorage(size);
      Arrays.fill(children, PageReference.empty());
      return children;
    }

    @Override
    void writeUnsavedRecursive(Chunk chunk, ByteBuf buf, LongArrayList toc) {
      if (complete) {
        super.writeUnsavedRecursive(chunk, buf, toc);
      }
      else if (!isSaved()) {
        writeChildrenRecursive(chunk, buf, toc);
      }
    }

    @Override
    public boolean isComplete() {
      return complete;
    }

    @Override
    public void setComplete() {
      recalculateTotalCount();
      complete = true;
    }

    @Override
    public void dump(StringBuilder buff) {
      super.dump(buff);
      buff.append(", complete=").append(complete);
    }
  }
}
