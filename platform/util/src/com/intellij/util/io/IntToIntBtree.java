/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.io;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.BitUtil;
import gnu.trove.TIntIntHashMap;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class IntToIntBtree {
  public static int version() {
    return 4 + (IOUtil.ourByteBuffersUseNativeByteOrder ? 0xFF : 0);
  }

  private static final int HAS_ZERO_KEY_MASK = 0xFF000000;
  static final boolean doSanityCheck = false;
  static final boolean doDump = false;

  final int pageSize;
  private final short maxInteriorNodes;
  private final short maxLeafNodes;
  private final short maxLeafNodesInHash;
  final BtreeRootNode root;
  private int height;
  private int maxStepsSearchedInHash;
  private int totalHashStepsSearched;
  private int hashSearchRequests;
  private int pagesCount;
  private int hashedPagesCount;
  private int count;
  private int movedMembersCount;
  private boolean hasZeroKey;
  private int zeroKeyValue;

  private final boolean isLarge = true;
  private final ResizeableMappedFile storage;
  private final boolean offloadToSiblingsBeforeSplit = false;
  private final boolean indexNodeIsHashTable = true;
  final int metaDataLeafPageLength;
  final int hashPageCapacity;

  private static final boolean hasCachedMappings = false;
  private TIntIntHashMap myCachedMappings;
  private final int myCachedMappingsSize;
  private static final int UNDEFINED_ADDRESS = -1;

  public IntToIntBtree(int pageSize, @NotNull File file, @NotNull PagedFileStorage.StorageLockContext storageLockContext, boolean initial) throws IOException {
    this.pageSize = pageSize;

    if (initial) {
      FileUtil.delete(file);
    }

    storage = new ResizeableMappedFile(file, pageSize, storageLockContext, 1024 * 1024, true, IOUtil.ourByteBuffersUseNativeByteOrder);
    storage.setRoundFactor(pageSize);
    root = new BtreeRootNode(this);

    if (initial) {
      root.setAddress(UNDEFINED_ADDRESS);
    }

    int i = (this.pageSize - BtreePage.RESERVED_META_PAGE_LEN) / BtreeIndexNodeView.INTERIOR_SIZE - 1;
    assert i < Short.MAX_VALUE && i % 2 == 0;
    maxInteriorNodes = (short)i;
    maxLeafNodes = (short)i;

    int metaPageLen;
    if (indexNodeIsHashTable) {
      ++i;
      while(!isPrime(i)) i -= 2;

      hashPageCapacity = i;
      metaPageLen = BtreePage.RESERVED_META_PAGE_LEN;
      i = (int)(hashPageCapacity * 0.9);
      if ((i & 1) == 1) ++i;
    }
    else {
      hashPageCapacity = -1;
      metaPageLen = BtreePage.RESERVED_META_PAGE_LEN;
    }

    metaDataLeafPageLength = metaPageLen;

    assert i > 0 && i % 2 == 0;
    maxLeafNodesInHash = (short) i;

    if (hasCachedMappings) {
      myCachedMappings = new TIntIntHashMap(myCachedMappingsSize = 4 * maxLeafNodes);
    }
    else {
      myCachedMappings = null;
      myCachedMappingsSize = -1;
    }
  }

  protected void doAllocateRoot() {
    nextPage(); // allocate root
    root.setAddress(0);
    root.getNodeView().setIndexLeaf(true);
  }

  // return total number of bytes needed for storing information
  public int persistVars(@NotNull BtreeDataStorage storage, boolean toDisk) {
    int i = storage.persistInt(0, height | (hasZeroKey ? HAS_ZERO_KEY_MASK :0), toDisk);
    hasZeroKey = (i & HAS_ZERO_KEY_MASK) != 0;
    height = i & ~HAS_ZERO_KEY_MASK;

    pagesCount = storage.persistInt(4, pagesCount, toDisk);
    movedMembersCount = storage.persistInt(8, movedMembersCount, toDisk);
    maxStepsSearchedInHash = storage.persistInt(12, maxStepsSearchedInHash, toDisk);
    count = storage.persistInt(16, count, toDisk);
    hashSearchRequests = storage.persistInt(20, hashSearchRequests, toDisk);
    totalHashStepsSearched = storage.persistInt(24, totalHashStepsSearched, toDisk);
    hashedPagesCount = storage.persistInt(28, hashedPagesCount, toDisk);
    root.setAddress(storage.persistInt(32, root.address, toDisk));
    zeroKeyValue = storage.persistInt(36, zeroKeyValue, toDisk);
    return 40;
  }

  public interface BtreeDataStorage {
    int persistInt(int offset, int value, boolean toDisk);
  }

  static class BtreeRootNode {
    int address;
    final BtreeIndexNodeView nodeView;
    boolean initialized;

    BtreeRootNode(IntToIntBtree btree) {
      nodeView = new BtreeIndexNodeView(btree);
    }

    void setAddress(int _address) {
      address = _address;
      initialized = false;
    }

    protected void syncWithStore() {
      nodeView.setAddress(address);
      initialized = true;
    }

    public BtreeIndexNodeView getNodeView() {
      if (!initialized) syncWithStore();
      return nodeView;
    }
  }

  private static boolean isPrime(int val) {
    if (val % 2 == 0) return false;
    int maxDivisor = (int)Math.sqrt(val);
    for(int i = 3; i <= maxDivisor; i+=2) {
      if (val % i == 0) return false;
    }
    return true;
  }

  private int nextPage() {
    int pageStart = (int)storage.length();
    storage.putInt(pageStart + pageSize - 4, 0);
    ++pagesCount;
    return pageStart;
  }

  private BtreeIndexNodeView myAccessNodeView;
  private int myLastGetKey;
  private int myOptimizedInserts;
  private boolean myCanUseLastKey;

  public boolean get(int key, @NotNull int[] result) {
    if (key == 0) {
      if (hasZeroKey) {
        result[0] = zeroKeyValue;
        return true;
      }
      return false;
    }

    if (hasCachedMappings) {
      if (myCachedMappings.containsKey(key)) {
        result[0] = myCachedMappings.get(key);
        return true;
      }
    }

    if (root.address == UNDEFINED_ADDRESS) return false;
    if (myAccessNodeView == null) myAccessNodeView = new BtreeIndexNodeView(this);
    myAccessNodeView.initTraversal(root.address);
    int index = myAccessNodeView.locate(key, false);

    if (index < 0) {
      myCanUseLastKey = true;
      myLastGetKey = key;
      return false;
    } else {
      myCanUseLastKey = false;
    }
    result[0] = myAccessNodeView.addressAt(index);
    return true;
  }

  public void put(int key, int value) {
    if (key == 0) {
      hasZeroKey = true;
      zeroKeyValue = value;
      return;
    }

    if (hasCachedMappings) {
      myCachedMappings.put(key, value);
      if (myCachedMappings.size() == myCachedMappingsSize) flushCachedMappings();
    }
    else {
      boolean canUseLastKey = myCanUseLastKey;
      if (canUseLastKey) {
        myCanUseLastKey = false;
        if (key == myLastGetKey && !myAccessNodeView.myHasFullPagesAlongPath && myAccessNodeView.isValid()) {
          ++myOptimizedInserts;
          ++count;
          myAccessNodeView.insert(key, value);
          return;
        }
      }
      doPut(key, value);
    }
  }

  private void doPut(int key, int value) {
    if (root.address == UNDEFINED_ADDRESS) doAllocateRoot();
    if (myAccessNodeView == null) myAccessNodeView = new BtreeIndexNodeView(this);
    myAccessNodeView.initTraversal(root.address);
    int index = myAccessNodeView.locate(key, true);

    if (index < 0) {
      ++count;
      myAccessNodeView.insert(key, value);
    } else {
      myAccessNodeView.setAddressAt(index, value);
      if (!myAccessNodeView.myIsDirty) myAccessNodeView.markDirty();
    }
  }

  void dumpStatistics() {
    int leafPages = height == 3 ? pagesCount - (1 + root.getNodeView().getChildrenCount() + 1):height == 2 ? pagesCount - 1:1;
    long leafNodesCapacity = hashedPagesCount * maxLeafNodesInHash + (leafPages - hashedPagesCount)* maxLeafNodes;
    long leafNodesCapacity2 = leafPages * maxLeafNodes;
    int usedPercent = (int)((count * 100L) / leafNodesCapacity);
    int usedPercent2 = (int)((count * 100L) / leafNodesCapacity2);
    IOStatistics.dump("pagecount:" + pagesCount +
                      ", height:" + height +
                      ", movedMembers:"+movedMembersCount +
                      ", optimized inserts:"+myOptimizedInserts +
                      ", hash steps:" + maxStepsSearchedInHash +
                      ", avg search in hash:" + (hashSearchRequests != 0 ? totalHashStepsSearched / hashSearchRequests:0) +
                      ", leaf pages used:" + usedPercent +
                      "%, leaf pages used if sorted: " +
                      usedPercent2 + "%, size:"+storage.length()
    );
  }

  private void flushCachedMappings() {
    if (hasCachedMappings) {
      int[] keys = myCachedMappings.keys();
      Arrays.sort(keys);
      for(int key:keys) doPut(key, myCachedMappings.get(key));
      myCachedMappings.clear();
      myCanUseLastKey = false;
    }
  }

  public void doClose() throws IOException {
    myCachedMappings = null;
    storage.close();
  }

  public void doFlush() {
    flushCachedMappings();
    storage.force();
  }

  static void myAssert(boolean b) {
    if (!b) {
      myAssert("breakpoint place" != "do not remove");
    }
    assert b;
  }

  static class BtreePage {
    static final int RESERVED_META_PAGE_LEN = 8;
    static final int FLAGS_SHIFT = 24;
    static final int LENGTH_SHIFT = 8;
    static final int LENGTH_MASK = 0xFFFF;

    protected final IntToIntBtree btree;
    protected int address = -1;
    private short myChildrenCount;
    protected int myAddressInBuffer;
    protected ByteBuffer myBuffer;
    protected ByteBufferWrapper myBufferWrapper;
    protected boolean myHasFullPagesAlongPath;
    protected boolean myIsDirty;

    public BtreePage(IntToIntBtree btree) {
      this.btree = btree;
      myChildrenCount = -1;
    }

    void setAddress(int _address) {
      setAddressInternal(_address);

      syncWithStore();
    }

    private final void setAddressInternal(int _address) {
      if (doSanityCheck) myAssert(_address % btree.pageSize == 0);
      address = _address;
    }

    protected void syncWithStore() {
      PagedFileStorage pagedFileStorage = btree.storage.getPagedFileStorage();
      myAddressInBuffer = pagedFileStorage.getOffsetInPage(address);
      myBufferWrapper = pagedFileStorage.getByteBuffer(address, false);
      myBuffer = myBufferWrapper.getCachedBuffer();
      myIsDirty = false; // we will mark dirty on child count change, attrs change or existing key put
      doInitFlags(myBuffer.getInt(myAddressInBuffer));
    }

    protected void doInitFlags(int anInt) {
      myChildrenCount = (short)((anInt >>> LENGTH_SHIFT) & LENGTH_MASK);
    }

    protected final void setFlag(int mask, boolean flag) {
      mask <<= FLAGS_SHIFT;
      int anInt = myBuffer.getInt(myAddressInBuffer);

      if (flag) anInt |= mask;
      else anInt &= ~ mask;
      myBuffer.putInt(myAddressInBuffer, anInt);
      if (!myIsDirty) markDirty();
    }

    void markDirty() {
      btree.storage.getPagedFileStorage().getByteBuffer(address, true);
      myIsDirty = true;
    }

    protected final short getChildrenCount() {
      return myChildrenCount;
    }

    protected final void setChildrenCount(short value) {
      myChildrenCount = value;
      int myValue = myBuffer.getInt(myAddressInBuffer);
      myValue &= ~LENGTH_MASK  <<  LENGTH_SHIFT;
      myValue |= value <<  LENGTH_SHIFT;
      myBuffer.putInt(myAddressInBuffer, myValue);
      if (!myIsDirty) markDirty();
    }

    protected final void setNextPage(int nextPage) {
      putInt(4, nextPage);
    }

    protected final int getNextPage() {
      return getInt(4);
    }

    protected final int getInt(int address) {
      return myBuffer.getInt(myAddressInBuffer + address);
    }

    protected final void putInt(int offset, int value) {
      myBuffer.putInt(myAddressInBuffer + offset, value);
    }

    protected final ByteBuffer getBytes(int address, int length) {
      ByteBuffer duplicate = myBuffer.duplicate();
      duplicate.order(myBuffer.order());

      int newPosition = address + myAddressInBuffer;
      duplicate.position(newPosition);
      duplicate.limit(newPosition + length);
      return duplicate;
    }

    protected final void putBytes(int address, ByteBuffer buffer) {
      myBuffer.position(address + myAddressInBuffer);
      myBuffer.put(buffer);
    }
  }

  // Leaf index node
  // (value_address {<0 if address in duplicates segment}, hash key) {getChildrenCount()}
  // (|next_node {<0} , hash key|) {getChildrenCount()} , next_node {<0}
  // next_node[i] is pointer to all less than hash_key[i] except for the last
  private static class BtreeIndexNodeView extends BtreePage {
    static final int INTERIOR_SIZE = 8;
    static final int KEY_OFFSET = 4;
    static final int MIN_ITEMS_TO_SHARE = 20;

    private boolean isIndexLeaf;
    private boolean isHashedLeaf;
    private static final int LARGE_MOVE_THRESHOLD = 5;

    BtreeIndexNodeView(IntToIntBtree btree) {
      super(btree);
    }

    private static final int HASH_FREE = 0;

    private int search(int value) {
      if (isIndexLeaf() && isHashedLeaf()) {
        return hashIndex(value);
      }
      else {
        int hi = getChildrenCount() - 1;
        int lo = 0;

        while(lo <= hi) {
          int mid = lo + (hi - lo) / 2;
          int keyAtMid = keyAt(mid);

          if (value > keyAtMid) {
            lo = mid + 1;
          } else if (value < keyAtMid) {
            hi = mid - 1;
          } else {
            return mid;
          }
        }
        return -(lo + 1);
      }
    }

    final int addressAt(int i) {
      if (doSanityCheck) {
        short childrenCount = getChildrenCount();
        if (isHashedLeaf()) myAssert(i < btree.hashPageCapacity);
        else myAssert(i < childrenCount || (!isIndexLeaf() && i == childrenCount));
      }
      return getInt(indexToOffset(i));
    }

    private void setAddressAt(int i, int value) {
      int offset = indexToOffset(i);
      if (doSanityCheck) {
        short childrenCount = getChildrenCount();
        final int metaPageLen;

        if (isHashedLeaf()) {
          myAssert(i < btree.hashPageCapacity);
          metaPageLen = btree.metaDataLeafPageLength;
        }
        else {
          myAssert(i < childrenCount || (!isIndexLeaf() && i == childrenCount));
          metaPageLen = RESERVED_META_PAGE_LEN;
        }
        myAssert(offset + 4 <= btree.pageSize);
        myAssert(offset >= metaPageLen);
      }
      putInt(offset, value);
    }

    private int indexToOffset(int i) {
      return i * INTERIOR_SIZE + (isHashedLeaf() ? btree.metaDataLeafPageLength:RESERVED_META_PAGE_LEN);
    }

    private int keyAt(int i) {
      if (doSanityCheck) {
        if (isHashedLeaf()) myAssert(i < btree.hashPageCapacity);
        else myAssert(i < getChildrenCount());
      }
      return getInt(indexToOffset(i) + KEY_OFFSET);
    }

    private void setKeyAt(int i, int value) {
      final int offset = indexToOffset(i) + KEY_OFFSET;
      if (doSanityCheck) {
        final int metaPageLen;
        if (isHashedLeaf()) {
          myAssert(i < btree.hashPageCapacity);
          metaPageLen = btree.metaDataLeafPageLength;
        }
        else {
          myAssert(i < getChildrenCount());
          metaPageLen = RESERVED_META_PAGE_LEN;
        }
        myAssert(offset + 4 <= btree.pageSize);
        myAssert(offset >= metaPageLen);
      }
      putInt(offset, value);
    }

    static final int INDEX_LEAF_MASK = 0x1;
    static final int HASHED_LEAF_MASK = 0x2;

    final boolean isIndexLeaf() {
      return isIndexLeaf;
    }

    @Override
    protected void doInitFlags(int flags) {
      super.doInitFlags(flags);
      flags = (flags >> FLAGS_SHIFT) & 0xFF;
      isHashedLeaf = BitUtil.isSet(flags, HASHED_LEAF_MASK);
      isIndexLeaf = BitUtil.isSet(flags, INDEX_LEAF_MASK);
    }

    void setIndexLeaf(boolean value) {
      isIndexLeaf = value;
      setFlag(INDEX_LEAF_MASK, value);
    }

    private boolean isHashedLeaf() {
      return isHashedLeaf;
    }

    void setHashedLeaf(boolean value) {
      isHashedLeaf = value;
      setFlag(HASHED_LEAF_MASK, value);
    }

    final short getMaxChildrenCount() {
      return isIndexLeaf() ? isHashedLeaf() ? btree.maxLeafNodesInHash:btree.maxLeafNodes:btree.maxInteriorNodes;
    }

    final boolean isFull() {
      short childrenCount = getChildrenCount();
      if (!isIndexLeaf()) {
        ++childrenCount;
      }
      return childrenCount == getMaxChildrenCount();
    }

    boolean processMappings(KeyValueProcessor processor) throws IOException {
      assert isIndexLeaf();

      if (isHashedLeaf()) {
        int offset = myAddressInBuffer + indexToOffset(0);

        for(int i = 0; i < btree.hashPageCapacity; ++i) {
          int key = myBuffer.getInt(offset + KEY_OFFSET);
          if (key != HASH_FREE) {
            if(!processor.process(key,  myBuffer.getInt(offset))) return false;
          }
          offset += INTERIOR_SIZE;
        }
      } else {
        final int childrenCount = getChildrenCount();
        for(int i = 0; i < childrenCount; ++i) {
          if (!processor.process(keyAt(i), addressAt(i))) return false;
        }
      }
      return true;
    }

    public void initTraversal(int address) {
      myHasFullPagesAlongPath = false;
      setAddress(address);
    }

    public boolean isValid() {
      return myBufferWrapper.getCachedBuffer() == myBuffer;
    }

    private static class HashLeafData {
      final BtreeIndexNodeView nodeView;
      final int[] keys;
      final TIntIntHashMap values;

      HashLeafData(BtreeIndexNodeView _nodeView, int recordCount) {
        nodeView = _nodeView;

        final IntToIntBtree btree = _nodeView.btree;

        int offset = nodeView.myAddressInBuffer + nodeView.indexToOffset(0);
        final ByteBuffer buffer = nodeView.myBuffer;

        keys = new int[recordCount];
        values = new TIntIntHashMap(recordCount);
        int keyNumber = 0;

        for(int i = 0; i < btree.hashPageCapacity; ++i) {
          int key = buffer.getInt(offset + KEY_OFFSET);
          if (key != HASH_FREE) {
            int value = buffer.getInt(offset);

            if (keyNumber == keys.length) throw new IllegalStateException("Index corrupted");
            keys[keyNumber++] = key;
            values.put(key, value);
          }
          offset += INTERIOR_SIZE;
        }

        Arrays.sort(keys);
      }

      private void clean() {
        final IntToIntBtree btree = nodeView.btree;
        for(int i = 0; i < btree.hashPageCapacity; ++i) {
          nodeView.setKeyAt(i, HASH_FREE);
        }
      }
    }

    private int splitNode(int parentAddress) {
      final boolean indexLeaf = isIndexLeaf();

      if (doSanityCheck) {
        myAssert(isFull());
        dump("before split:"+indexLeaf);
      }

      final boolean hashedLeaf = isHashedLeaf();
      final short recordCount = getChildrenCount();
      BtreeIndexNodeView parent = null;
      HashLeafData hashLeafData = null;

      if (parentAddress != 0) {
        parent = new BtreeIndexNodeView(btree);
        parent.setAddress(parentAddress);

        if (btree.offloadToSiblingsBeforeSplit) {
          if (hashedLeaf) {
            hashLeafData = new HashLeafData(this, recordCount);
            if (doOffloadToSiblingsWhenHashed(parent, hashLeafData)) return parentAddress;
          }
          else {
            if (doOffloadToSiblingsSorted(parent)) return parentAddress;
          }
        }
      }

      short maxIndex = (short)(getMaxChildrenCount() / 2);

      BtreeIndexNodeView newIndexNode = new BtreeIndexNodeView(btree);
      newIndexNode.setAddress(btree.nextPage());
      syncWithStore(); // next page can cause ByteBuffer to be invalidated!
      if (parent != null) parent.syncWithStore();
      btree.root.syncWithStore();

      newIndexNode.setIndexLeaf(indexLeaf); // newIndexNode becomes dirty

      int nextPage = getNextPage();
      setNextPage(newIndexNode.address);
      newIndexNode.setNextPage(nextPage);

      int medianKey = -1;

      if (indexLeaf && hashedLeaf) {
        if (hashLeafData == null) hashLeafData = new HashLeafData(this, recordCount);
        final int[] keys = hashLeafData.keys;

        boolean defaultSplit = true;

        //if (keys[keys.length - 1] < newValue && btree.height <= 3) {  // optimization for adding element to last block
        //  btree.root.syncWithStore();
        //  if (btree.height == 2 && btree.root.search(keys[0]) == btree.root.getChildrenCount() - 1) {
        //    defaultSplit = false;
        //  } else if (btree.height == 3 &&
        //             btree.root.search(keys[0]) == -btree.root.getChildrenCount() - 1 &&
        //             parent.search(keys[0]) == parent.getChildrenCount() - 1
        //            ) {
        //    defaultSplit = false;
        //  }
        //
        //  if (!defaultSplit) {
        //    newIndexNode.setChildrenCount((short)0);
        //    newIndexNode.insert(newValue, 0);
        //    ++btree.count;
        //    medianKey = newValue;
        //  }
        //}

        if (defaultSplit) {
          hashLeafData.clean();

          final TIntIntHashMap map = hashLeafData.values;

          final int avg = keys.length / 2;
          medianKey = keys[avg];
          --btree.hashedPagesCount;
          setChildrenCount((short)0);  // this node becomes dirty
          newIndexNode.setChildrenCount((short)0);

          for(int i = 0; i < avg; ++i) {
            int key = keys[i];
            insert(key, map.get(key));
            key = keys[avg + i];
            newIndexNode.insert(key, map.get(key));
          }

          /*setHashedLeaf(false);
                  setChildrenCount((short)keys.length);

                  --btree.hashedPagesCount;
                  btree.movedMembersCount += keys.length;

                  for(int i = 0; i < keys.length; ++i) {
                    int key = keys[i];
                    setKeyAt(i, key);
                    setAddressAt(i, map.get(key));
                  }
                  return parentAddress;*/
        }
      } else {
        short recordCountInNewNode = (short)(recordCount - maxIndex);
        newIndexNode.setChildrenCount(recordCountInNewNode); // newIndexNode node becomes dirty

        if (btree.isLarge) {
          ByteBuffer buffer = getBytes(indexToOffset(maxIndex), recordCountInNewNode * INTERIOR_SIZE);
          newIndexNode.putBytes(newIndexNode.indexToOffset(0), buffer);
        } else {
          for(int i = 0; i < recordCountInNewNode; ++i) {
            newIndexNode.setAddressAt(i, addressAt(i + maxIndex));
            newIndexNode.setKeyAt(i, keyAt(i + maxIndex));
          }
        }
        if (indexLeaf) {
          medianKey = newIndexNode.keyAt(0);
        } else {
          newIndexNode.setAddressAt(recordCountInNewNode, addressAt(recordCount));
          --maxIndex;
          medianKey = keyAt(maxIndex);     // key count is odd (since children count is even) and middle key goes to parent
        }
        setChildrenCount(maxIndex); // "this" node becomes dirty
      }

      if (parent != null) {
        if (doSanityCheck) {
          int medianKeyInParent = parent.search(medianKey);
          int ourKey = keyAt(0);
          int ourKeyInParent = parent.search(ourKey);
          parent.dump("About to insert "+medianKey + "," + newIndexNode.address+"," + medianKeyInParent + " our key " + ourKey + ", " + ourKeyInParent);

          myAssert(medianKeyInParent < 0);
          myAssert(!parent.isFull());
        }

        parent.insert(medianKey, -newIndexNode.address);

        if (doSanityCheck) {
          parent.dump("After modifying parent");
          int search = parent.search(medianKey);
          myAssert(search >= 0);
          myAssert(parent.addressAt(search + 1) == -newIndexNode.address);

          dump("old node after split:");
          newIndexNode.dump("new node after split:");
        }
      } else {
        if (doSanityCheck) {
          btree.root.getNodeView().dump("Splitting root:"+medianKey);
        }

        int newRootAddress = btree.nextPage();
        newIndexNode.syncWithStore();
        syncWithStore();

        if (doSanityCheck) {
          System.out.println("Pages:"+btree.pagesCount+", elements:"+btree.count + ", average:" + (btree.height + 1));
        }
        btree.root.setAddress(newRootAddress);
        parentAddress = newRootAddress;

        BtreeIndexNodeView rootNodeView = btree.root.getNodeView();
        rootNodeView.setChildrenCount((short)1); // btree.root becomes dirty
        rootNodeView.setKeyAt(0, medianKey);
        rootNodeView.setAddressAt(0, -address);
        rootNodeView.setAddressAt(1, -newIndexNode.address);

        if (doSanityCheck) {
          rootNodeView.dump("New root");
          dump("First child");
          newIndexNode.dump("Second child");
        }
      }

      return parentAddress;
    }

    private boolean doOffloadToSiblingsWhenHashed(BtreeIndexNodeView parent, final HashLeafData hashLeafData) {
      int indexInParent = parent.search(hashLeafData.keys[0]);

      if (indexInParent >= 0) {
        BtreeIndexNodeView sibling = new BtreeIndexNodeView(btree);
        sibling.setAddress(-parent.addressAt(indexInParent));

        int numberOfKeysToMove = (sibling.getMaxChildrenCount() - sibling.getChildrenCount()) / 2;

        if (!sibling.isFull() && numberOfKeysToMove > MIN_ITEMS_TO_SHARE) {
          if (doSanityCheck) {
            sibling.dump("Offloading to left sibling");
            parent.dump("parent before");
          }

          final int childrenCount = getChildrenCount();
          final int[] keys = hashLeafData.keys;
          final TIntIntHashMap map = hashLeafData.values;

          for(int i = 0; i < numberOfKeysToMove; ++i) {
            final int key = keys[i];
            sibling.insert(key, map.get(key));
          }

          if (doSanityCheck) {
            sibling.dump("Left sibling after");
          }

          parent.setKeyAt(indexInParent, keys[numberOfKeysToMove]);
          if (!parent.myIsDirty) parent.markDirty();

          setChildrenCount((short)0); // "this" node becomes dirty
          --btree.hashedPagesCount;
          hashLeafData.clean();

          for(int i = numberOfKeysToMove; i < childrenCount; ++i) {
            final int key = keys[i];
            insert(key, map.get(key));
          }
        } else if (indexInParent + 1 < parent.getChildrenCount()) {
          insertToRightSiblingWhenHashed(parent, hashLeafData, indexInParent, sibling);
        }
      } else if (indexInParent == -1) {
        insertToRightSiblingWhenHashed(parent, hashLeafData, 0, new BtreeIndexNodeView(btree));
      }

      if (!isFull()) {
        if (doSanityCheck) {
          dump("old node after split:");
          parent.dump("Parent node after split");
        }
        return true;
      }

      return false;
    }

    private void insertToRightSiblingWhenHashed(BtreeIndexNodeView parent,
                                                HashLeafData hashLeafData,
                                                int indexInParent,
                                                BtreeIndexNodeView sibling) {
      sibling.setAddress(-parent.addressAt(indexInParent + 1));
      int numberOfKeysToMove = (sibling.getMaxChildrenCount() - sibling.getChildrenCount()) / 2;

      if (!sibling.isFull() && numberOfKeysToMove > MIN_ITEMS_TO_SHARE) {
        if (doSanityCheck) {
          sibling.dump("Offloading to right sibling");
          parent.dump("parent before");
        }

        final int[] keys = hashLeafData.keys;
        final TIntIntHashMap map = hashLeafData.values;

        final int childrenCount = getChildrenCount();
        final int lastChildIndex = childrenCount - numberOfKeysToMove;
        for(int i = lastChildIndex; i < childrenCount; ++i) {
          final int key = keys[i];
          sibling.insert(key, map.get(key)); // sibling will be dirty
        }

        if (doSanityCheck) {
          sibling.dump("Right sibling after");
        }
        parent.setKeyAt(indexInParent, keys[lastChildIndex]);
        if (!parent.myIsDirty) parent.markDirty();

        setChildrenCount((short)0); // "this" node becomes dirty
        --btree.hashedPagesCount;
        hashLeafData.clean();

        for(int i = 0; i < lastChildIndex; ++i) {
          final int key = keys[i];
          insert(key, map.get(key));
        }
      }
    }

    private boolean doOffloadToSiblingsSorted(BtreeIndexNodeView parent) {
      if (!isIndexLeaf()) return false; // TODO

      int indexInParent = parent.search(keyAt(0));

      if (indexInParent >= 0) {
        if (doSanityCheck) {
          myAssert(parent.keyAt(indexInParent) == keyAt(0));
          myAssert(parent.addressAt(indexInParent + 1) == -address);
        }

        BtreeIndexNodeView sibling = new BtreeIndexNodeView(btree);
        sibling.setAddress(-parent.addressAt(indexInParent));

        final int toMove = (sibling.getMaxChildrenCount() - sibling.getChildrenCount()) / 2;

        if (toMove > 0) {
          if (doSanityCheck) {
            sibling.dump("Offloading to left sibling");
            parent.dump("parent before");
          }

          for(int i = 0; i < toMove; ++i) sibling.insert(keyAt(i), addressAt(i));
          if (doSanityCheck) {
            sibling.dump("Left sibling after");
          }

          parent.setKeyAt(indexInParent, keyAt(toMove));
          if (!parent.myIsDirty) parent.markDirty();

          int indexOfLastChildToMove = (int)getChildrenCount() - toMove;
          btree.movedMembersCount += indexOfLastChildToMove;

          if (btree.isLarge) {
            ByteBuffer buffer = getBytes(indexToOffset(toMove), indexOfLastChildToMove * INTERIOR_SIZE);
            putBytes(indexToOffset(0), buffer);
          }
          else {
            for (int i = 0; i < indexOfLastChildToMove; ++i) {
              setAddressAt(i, addressAt(i + toMove));
              setKeyAt(i, keyAt(i + toMove));
            }
          }

          setChildrenCount((short)indexOfLastChildToMove);  // "this" node becomes dirty
        }
        else if (indexInParent + 1 < parent.getChildrenCount()) {
          insertToRightSiblingWhenSorted(parent, indexInParent + 1, sibling);
        }
      } else if (indexInParent == -1) {
        insertToRightSiblingWhenSorted(parent, 0, new BtreeIndexNodeView(btree));
      }

      if (!isFull()) {
        if (doSanityCheck) {
          dump("old node after split:");
          parent.dump("Parent node after split");
        }
        return true;
      }
      return false;
    }

    private void insertToRightSiblingWhenSorted(BtreeIndexNodeView parent, int indexInParent, BtreeIndexNodeView sibling) {
      sibling.setAddress(-parent.addressAt(indexInParent + 1));
      int toMove = (sibling.getMaxChildrenCount() - sibling.getChildrenCount()) / 2;

      if (toMove > 0) {
        if (doSanityCheck) {
          sibling.dump("Offloading to right sibling");
          parent.dump("parent before");
        }

        int childrenCount = getChildrenCount();
        int lastChildIndex = childrenCount - toMove;
        for(int i = lastChildIndex; i < childrenCount; ++i) sibling.insert(keyAt(i), addressAt(i)); // sibling will be dirty
        if (doSanityCheck) {
          sibling.dump("Right sibling after");
        }
        parent.setKeyAt(indexInParent, keyAt(lastChildIndex));
        if (!parent.myIsDirty) parent.markDirty();
        setChildrenCount((short)lastChildIndex); // "this" node becomes dirty
      }
    }

    protected void dump(String s) {
      if (doDump) {
        immediateDump(s);
      }
    }

    private void immediateDump(String s) {
      short maxIndex = getChildrenCount();
      System.out.println(s + " @" + address);
      for (int i = 0; i < maxIndex; ++i) {
        System.out.print(addressAt(i) + " " + keyAt(i) + " ");
      }

      if (!isIndexLeaf()) {
        System.out.println(addressAt(maxIndex));
      }
      else {
        System.out.println();
      }
    }

    private int locate(int valueHC, boolean split) {
      int searched = 0;
      int parentAddress = 0;
      final int maxHeight = btree.height + 1;

      while(true) {
        if (isFull()) {
          if (split) {
            parentAddress = splitNode(parentAddress);
            if (parentAddress != 0) setAddress(parentAddress);
            --searched;
          } else {
            myHasFullPagesAlongPath = true;
          }
        }

        int i = search(valueHC);

        ++searched;
        if (searched > maxHeight) throw new IllegalStateException();

        if (isIndexLeaf()) {
          btree.height = Math.max(btree.height, searched);
          return i;
        }

        int address = i < 0 ? addressAt(-i - 1):addressAt(i + 1);
        parentAddress = this.address;
        setAddress(-address);
      }
    }

    private void insert(int valueHC, int newValueId) {
      if (doSanityCheck) myAssert(!isFull());
      short recordCount = getChildrenCount();
      if (doSanityCheck) myAssert(recordCount < getMaxChildrenCount());

      final boolean indexLeaf = isIndexLeaf();

      if (indexLeaf) {
        if (recordCount == 0 && btree.indexNodeIsHashTable) {
          setHashedLeaf(true);
          ++btree.hashedPagesCount;
        }

        if (isHashedLeaf()) {
          int index = hashIndex(valueHC);

          if (index < 0) {
            index = -index - 1;
          }

          setKeyAt(index, valueHC);
          setAddressAt(index, newValueId);
          setChildrenCount((short)(recordCount + 1)); // "this" node becomes dirty
          return;
        }
      }

      int medianKeyInParent = search(valueHC);
      if (doSanityCheck) myAssert(medianKeyInParent < 0);
      int index = -medianKeyInParent - 1;
      setChildrenCount((short)(recordCount + 1)); // "this" node becomes dirty

      final int itemsToMove = recordCount - index;
      btree.movedMembersCount += itemsToMove;

      if (indexLeaf) {
        if (btree.isLarge && itemsToMove > LARGE_MOVE_THRESHOLD) {
          ByteBuffer buffer = getBytes(indexToOffset(index), itemsToMove * INTERIOR_SIZE);
          putBytes(indexToOffset(index + 1), buffer);
        } else {
          for(int i = recordCount - 1; i >= index; --i) {
            setKeyAt(i + 1, keyAt(i));
            setAddressAt(i + 1, addressAt(i));
          }
        }
        setKeyAt(index, valueHC);
        setAddressAt(index, newValueId);
      } else {
        // <address> (<key><address>) {record_count - 1}
        //
        setAddressAt(recordCount + 1, addressAt(recordCount));
        if (btree.isLarge && itemsToMove > LARGE_MOVE_THRESHOLD) {
          int elementsAfterIndex = recordCount - index - 1;
          if (elementsAfterIndex > 0) {
            ByteBuffer buffer = getBytes(indexToOffset(index + 1), elementsAfterIndex * INTERIOR_SIZE);
            putBytes(indexToOffset(index + 2), buffer);
          }
        } else {
          for(int i = recordCount - 1; i > index; --i) {
            setKeyAt(i + 1, keyAt(i));
            setAddressAt(i + 1, addressAt(i));
          }
        }

        if (index < recordCount) setKeyAt(index + 1, keyAt(index));

        setKeyAt(index, valueHC);
        setAddressAt(index + 1, newValueId);
      }

      if (doSanityCheck) {
        if (index > 0) myAssert(keyAt(index - 1) < keyAt(index));
        if (index < recordCount) myAssert(keyAt(index) < keyAt(index + 1));
      }
    }

    private static final boolean useDoubleHash = true;
    private int hashIndex(int value) {
      final int length = btree.hashPageCapacity;
      int hash = value & 0x7fffffff;
      int index = hash % length;
      int keyAtIndex = keyAt(index);

      btree.hashSearchRequests++;

      int total = 0;
      if (useDoubleHash) {
        if (keyAtIndex != value && keyAtIndex != HASH_FREE) {
          // see Knuth, p. 529
          final int probe = 1 + (hash % (length - 2));

          do {
            index -= probe;
            if (index < 0) index += length;

            keyAtIndex = keyAt(index);
            ++total;
            if (total > length) {
              throw new IllegalStateException("Index corrupted"); // violation of Euler's theorem
            }
          }
          while (keyAtIndex != value && keyAtIndex != HASH_FREE);
        }
      } else {
        while(keyAtIndex != value && keyAtIndex != HASH_FREE) {
          if (index == 0) index = length;
          --index;
          keyAtIndex = keyAt(index);
          ++total;

          if (total > length) throw new IllegalStateException("Index corrupted"); // violation of Euler's theorem
        }
      }

      btree.maxStepsSearchedInHash = Math.max(btree.maxStepsSearchedInHash, total);
      btree.totalHashStepsSearched += total;

      return keyAtIndex == HASH_FREE ? -index - 1 : index;
    }
  }

  public abstract static class KeyValueProcessor {
    public abstract boolean process(int key, int value) throws IOException;
  }

  public boolean processMappings(@NotNull KeyValueProcessor processor) throws IOException {
    doFlush();
    
    if (hasZeroKey) {
      if (!processor.process(0, zeroKeyValue)) return false;
    }

    if(root.address == UNDEFINED_ADDRESS) return true;
    root.syncWithStore();
    
    return processLeafPages(root.getNodeView(), processor);
  }

  private boolean processLeafPages(@NotNull BtreeIndexNodeView node, @NotNull KeyValueProcessor processor) throws IOException {
    if (node.isIndexLeaf()) {
      return node.processMappings(processor);
    }

    // Copy children addresses first to avoid node's ByteBuffer invalidation
    final int[] childrenAddresses = new int[node.getChildrenCount() + 1];

    for(int i = 0; i < childrenAddresses.length; ++i) {
      childrenAddresses[i] = -node.addressAt(i);
    }

    if (childrenAddresses.length > 0) {
      BtreeIndexNodeView child = new BtreeIndexNodeView(this);

      for (int childrenAddress : childrenAddresses) {
        child.setAddress(childrenAddress);
        if (!processLeafPages(child, processor)) return false;
      }
    }
    return true;
  }
}
