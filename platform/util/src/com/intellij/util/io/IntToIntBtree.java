package com.intellij.util.io;

import com.intellij.openapi.util.io.FileUtil;
import gnu.trove.TIntIntHashMap;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
* Created by IntelliJ IDEA.
* User: maximmossienko
* Date: 7/12/11
* Time: 1:34 PM
*/
class IntToIntBtree {
  static final int VERSION = 2;
  static final boolean doSanityCheck = false;
  static final boolean doDump = false;

  final int pageSize;
  private final short maxInteriorNodes;
  private final short maxLeafNodes;
  private final short maxLeafNodesInHash;
  final BtreeIndexNodeView root;
  private int height;
  private int maxStepsSearchedInHash;
  private int totalHashStepsSearched;
  private int hashSearchRequests;
  private int pagesCount;
  private int hashedPagesCount;
  private int count;
  private int movedMembersCount;

  private boolean isLarge = true;
  private final ResizeableMappedFile storage;
  private final boolean offloadToSiblingsBeforeSplit = false;
  private boolean indexNodeIsHashTable = true;
  final int metaDataLeafPageLength;
  final int hashPageCapacity;

  private static final boolean hasCachedMappings = false;
  private TIntIntHashMap myCachedMappings;
  private final int myCachedMappingsSize;

  public IntToIntBtree(int _pageSize, File file, boolean initial) throws IOException {
    pageSize = _pageSize;

    if (initial) {
      FileUtil.delete(file);
    }

    storage = new ResizeableMappedFile(file, pageSize, PersistentEnumeratorBase.ourLock, 1024 * 1024, true);
    root = new BtreeIndexNodeView(this);

    if (initial) {
      nextPage(); // allocate root
      root.setAddress(0);
      root.setIndexLeaf(true);
    }

    int i = (pageSize - BtreePage.RESERVED_META_PAGE_LEN) / BtreeIndexNodeView.INTERIOR_SIZE - 1;
    assert i < Short.MAX_VALUE && i % 2 == 0;
    maxInteriorNodes = (short)i;
    maxLeafNodes = (short)i;

    int metaPageLen = BtreePage.RESERVED_META_PAGE_LEN;

    if (indexNodeIsHashTable) {
      ++i;
      final double bitsPerState = BtreeIndexNodeView.haveDeleteState? 2d:1d;
      while(Math.ceil(bitsPerState * i / 8) + i * BtreeIndexNodeView.INTERIOR_SIZE + BtreePage.RESERVED_META_PAGE_LEN > pageSize ||
            !isPrime(i)
           ) {
        i -= 2;
      }

      hashPageCapacity = i;
      metaPageLen = BtreePage.RESERVED_META_PAGE_LEN + (int)Math.ceil(bitsPerState * hashPageCapacity / 8);
      i = (int)(hashPageCapacity * 0.8);
      if ((i & 1) == 1) ++i;
    } else {
      hashPageCapacity = -1;
    }

    metaDataLeafPageLength = metaPageLen;

    assert i > 0 && i % 2 == 0;
    maxLeafNodesInHash = (short) i;

    if (hasCachedMappings) {
      myCachedMappings = new TIntIntHashMap(myCachedMappingsSize = 4 * maxLeafNodes);
    } else {
      myCachedMappings = null;
      myCachedMappingsSize = -1;
    }
  }

  public void persistVars(BtreeDataStorage storage, boolean toDisk) {
    height = storage.persistInt(0, height, toDisk);
    pagesCount = storage.persistInt(4, pagesCount, toDisk);
    movedMembersCount = storage.persistInt(8, movedMembersCount, toDisk);
    maxStepsSearchedInHash = storage.persistInt(12, maxStepsSearchedInHash, toDisk);
    count = storage.persistInt(16, count, toDisk);
    hashSearchRequests = storage.persistInt(20, hashSearchRequests, toDisk);
    totalHashStepsSearched = storage.persistInt(24, totalHashStepsSearched, toDisk);
    hashedPagesCount = storage.persistInt(28, hashedPagesCount, toDisk);
    root.setAddress(storage.persistInt(32, root.address, toDisk));
  }

  interface BtreeDataStorage {
    int persistInt(int offset, int value, boolean toDisk);
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

  public @Nullable Integer get(int key) {
    if (hasCachedMappings) {
      if (myCachedMappings.containsKey(key)) {
        return myCachedMappings.get(key);
      }
    }

    BtreeIndexNodeView currentIndexNode = new BtreeIndexNodeView(this);
    currentIndexNode.setAddress(root.address);
    int index = currentIndexNode.locate(key, false);

    if (index < 0) return null;
    return currentIndexNode.addressAt(index);
  }

  public void put(int key, int value) {
    if (hasCachedMappings) {
      myCachedMappings.put(key, value);
      if (myCachedMappings.size() == myCachedMappingsSize) flushCachedMappings();
    } else {
      doPut(key, value);
    }
  }

  private void doPut(int key, int value) {
    BtreeIndexNodeView currentIndexNode = new BtreeIndexNodeView(this);
    currentIndexNode.setAddress(root.address);
    int index = currentIndexNode.locate(key, true);

    if (index < 0) {
      ++count;
      currentIndexNode.insert(key, value);
    } else {
      currentIndexNode.setAddressAt(index, value);
    }
  }

  //public int remove(int key) {
  //  // TODO
  //  BtreeIndexNodeView currentIndexNode = new BtreeIndexNodeView(this);
  //  currentIndexNode.setAddress(root.address);
  //  int index = currentIndexNode.locate(key, false);
  //  myAssert(BtreeIndexNodeView.haveDeleteState);
  //  throw new UnsupportedOperationException("Remove does not work yet "+key);
  //}

  void dumpStatistics() {
    int leafPages = height == 3 ? pagesCount - (1 + root.getChildrenCount() + 1):height == 2 ? pagesCount - 1:1;
    long leafNodesCapacity = hashedPagesCount * maxLeafNodesInHash + (leafPages - hashedPagesCount)* maxLeafNodes;
    long leafNodesCapacity2 = leafPages * maxLeafNodes;
    int usedPercent = (int)((count * 100L) / leafNodesCapacity);
    int usedPercent2 = (int)((count * 100L) / leafNodesCapacity2);
    IOStatistics.dump("pagecount:" + pagesCount + ", height:" + height + ", movedMembers:"+movedMembersCount +
                      ", hash steps:" + maxStepsSearchedInHash + ", avg search in hash:" + (hashSearchRequests != 0 ? totalHashStepsSearched / hashSearchRequests:0) +
                      ", leaf pages used:" + usedPercent + "%, leaf pages used if sorted: " + usedPercent2 + "%, size:"+storage.length() );
  }

  private void flushCachedMappings() {
    if (hasCachedMappings) {
      int[] keys = myCachedMappings.keys();
      Arrays.sort(keys);
      for(int key:keys) doPut(key, myCachedMappings.get(key));
      myCachedMappings.clear();
    }
  }

  void doClose() throws IOException {
    myCachedMappings = null;
    storage.close();
  }

  void doFlush() {
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

    protected final IntToIntBtree btree;
    protected int address = -1;
    private short myChildrenCount;
    protected int myAddressInBuffer;
    protected ByteBuffer myBuffer;

    public BtreePage(IntToIntBtree btree) {
      this.btree = btree;
      myChildrenCount = -1;
    }

    void setAddress(int _address) {
      if (doSanityCheck) myAssert(_address % btree.pageSize == 0);
      address = _address;

      syncWithStore();
    }

    protected void syncWithStore() {
      myChildrenCount = -1;
      PagedFileStorage pagedFileStorage = btree.storage.getPagedFileStorage();
      myAddressInBuffer = pagedFileStorage.getOffsetInPage(address);
      myBuffer = pagedFileStorage.getByteBuffer(address);
    }

    protected final void setFlag(int mask, boolean flag) {
      byte b = myBuffer.get(myAddressInBuffer);
      if (flag) b |= mask;
      else b &= ~mask;
      myBuffer.put(myAddressInBuffer, b);
    }

    protected final short getChildrenCount() {
      if (myChildrenCount == -1) {
        myChildrenCount = myBuffer.getShort(myAddressInBuffer + 1);
      }
      return myChildrenCount;
    }

    protected final void setChildrenCount(short value) {
      myChildrenCount = value;
      myBuffer.putShort(myAddressInBuffer + 1, value);
    }

    protected final void setNextPage(int nextPage) {
      putInt(3, nextPage);
    }

    // TODO: use it
    protected final int getNextPage() {
      return getInt(3);
    }

    protected final int getInt(int address) {
      return myBuffer.getInt(myAddressInBuffer + address);
    }

    protected final void putInt(int offset, int value) {
      myBuffer.putInt(myAddressInBuffer + offset, value);
    }

    protected final ByteBuffer getBytes(int address, int length) {
      ByteBuffer duplicate = myBuffer.duplicate();

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
  static class BtreeIndexNodeView extends BtreePage {
    static final int INTERIOR_SIZE = 8;
    static final int KEY_OFFSET = 4;
    static final int MIN_ITEMS_TO_SHARE = 20;

    private boolean isIndexLeaf;
    private boolean flagsSet;
    private boolean isHashedLeaf;
    private static final int LARGE_MOVE_THRESHOLD = 5;

    BtreeIndexNodeView(IntToIntBtree btree) {
      super(btree);
    }

    @Override
    protected void syncWithStore() {
      super.syncWithStore();
      flagsSet = false;
    }

    static final int HASH_FREE = 0;
    static final int HASH_FULL = 1;
    static final int HASH_REMOVED = 2;

    int search(int value) {
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

    private final int indexToOffset(int i) {
      return i * INTERIOR_SIZE + (isHashedLeaf() ? btree.metaDataLeafPageLength:RESERVED_META_PAGE_LEN);
    }

    private final int keyAt(int i) {
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
      if (!flagsSet) initFlags();
      return isIndexLeaf;
    }

    private void initFlags() {
      byte flags = myBuffer.get(myAddressInBuffer);
      isHashedLeaf = (flags & HASHED_LEAF_MASK) == HASHED_LEAF_MASK;
      isIndexLeaf = (flags & INDEX_LEAF_MASK) == INDEX_LEAF_MASK;
    }

    void setIndexLeaf(boolean value) {
      isIndexLeaf = value;
      setFlag(INDEX_LEAF_MASK, value);
    }

    private final boolean isHashedLeaf() {
      if (!flagsSet) initFlags();
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

    int[] exportKeys() {
      assert isIndexLeaf();
      short childrenCount = getChildrenCount();
      int[] keys = new int[childrenCount];

      if (isHashedLeaf()) {
        final int offset = myAddressInBuffer + indexToOffset(0) + KEY_OFFSET;

        int keyNumber = 0;

        for(int i = 0; i < btree.hashPageCapacity; ++i) {
          if (hashGetState(i) == HASH_FULL) {
            int key = myBuffer.getInt(offset + i * INTERIOR_SIZE);
            keys[keyNumber++] = key;
          }
        }
      } else {
        for(int i = 0; i < childrenCount; ++i) {
          keys[i] = keyAt(i);
        }
      }
      return keys;
    }
    
    static class HashLeafData {
      final BtreeIndexNodeView nodeView;
      final int[] keys;
      final TIntIntHashMap values;
      
      HashLeafData(BtreeIndexNodeView _nodeView, int recordCount) {
        nodeView = _nodeView;

        final IntToIntBtree btree = _nodeView.btree;

        final int offset = nodeView.myAddressInBuffer + nodeView.indexToOffset(0);
        final ByteBuffer buffer = nodeView.myBuffer;
        
        keys = new int[recordCount];
        values = new TIntIntHashMap(recordCount);
        int keyNumber = 0;
        
        for(int i = 0; i < btree.hashPageCapacity; ++i) {
          if (nodeView.hashGetState(i) == HASH_FULL) {
            int key = buffer.getInt(offset + i * INTERIOR_SIZE + KEY_OFFSET);
            keys[keyNumber++] = key;
            int value = buffer.getInt(offset + i * INTERIOR_SIZE);
            values.put(key, value);
          }
        }
        
        Arrays.sort(keys);
      }

      void clean() {
        final IntToIntBtree btree = nodeView.btree;
        for(int i = 0; i < btree.hashPageCapacity; ++i) {
          nodeView.hashSetState(i, HASH_FREE);
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
          } else {
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

      newIndexNode.setIndexLeaf(indexLeaf);

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
          setChildrenCount((short)0);
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
        newIndexNode.setChildrenCount(recordCountInNewNode);
        
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
        setChildrenCount(maxIndex);
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
          btree.root.dump("Splitting root:"+medianKey);
        }

        int newRootAddress = btree.nextPage();
        newIndexNode.syncWithStore();
        syncWithStore();

        if (doSanityCheck) {
          System.out.println("Pages:"+btree.pagesCount+", elements:"+btree.count + ", average:" + (btree.height + 1));
        }
        btree.root.setAddress(newRootAddress);
        parentAddress = newRootAddress;

        btree.root.setChildrenCount((short)1);
        btree.root.setKeyAt(0, medianKey);
        btree.root.setAddressAt(0, -address);
        btree.root.setAddressAt(1, -newIndexNode.address);


        if (doSanityCheck) {
          btree.root.dump("New root");
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
          
          setChildrenCount((short)0);
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
          sibling.insert(key, map.get(key));
        }
        
        if (doSanityCheck) {
          sibling.dump("Right sibling after");
        }
        parent.setKeyAt(indexInParent, keys[lastChildIndex]);

        setChildrenCount((short)0);
        --btree.hashedPagesCount;
        hashLeafData.clean();

        for(int i = 0; i < lastChildIndex; ++i) {
          final int key = keys[i];
          insert(key, map.get(key));
        }
      }
    }
    
    private boolean doOffloadToSiblingsSorted(BtreeIndexNodeView parent) {
      boolean indexLeaf = isIndexLeaf();
      if (!indexLeaf) return false; // TODO

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

          setChildrenCount((short)indexOfLastChildToMove);
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
        for(int i = lastChildIndex; i < childrenCount; ++i) sibling.insert(keyAt(i), addressAt(i));
        if (doSanityCheck) {
          sibling.dump("Right sibling after");
        }
        parent.setKeyAt(indexInParent, keyAt(lastChildIndex));
        setChildrenCount((short)lastChildIndex);
      }
    }

    private void dump(String s) {
      if (doDump) {
        immediateDump(s);
      }
    }

    private void immediateDump(String s) {
        short maxIndex = getChildrenCount();
        System.out.println(s + " @" + address);
        for(int i = 0; i < maxIndex; ++i) {
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

      while(true) {
        if (split && isFull()) {
          parentAddress = splitNode(parentAddress);
          if (parentAddress != 0) setAddress(parentAddress);
          --searched;
        }

        int i = search(valueHC);

        ++searched;

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
          int index = hashInsertionIndex(valueHC);

          if (index < 0) {
            index = -index - 1;
          }

          setKeyAt(index, valueHC);
          hashSetState(index, HASH_FULL);
          setAddressAt(index, newValueId);
          setChildrenCount((short)(recordCount + 1));

          return;
        }
      }

      int medianKeyInParent = search(valueHC);
      if (doSanityCheck) myAssert(medianKeyInParent < 0);
      int index = -medianKeyInParent - 1;
      setChildrenCount((short)(recordCount + 1));

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

    private int hashIndex(int value) {
      int hash, probe, index;

      final int length = btree.hashPageCapacity;
      hash = hash(value) & 0x7fffffff;
      index = hash % length;
      int state = hashGetState(index);
      int total = 0;

      btree.hashSearchRequests++;

      if (state != HASH_FREE &&
          (state == HASH_REMOVED || keyAt(index) != value)) {
        // see Knuth, p. 529
        probe = 1 + (hash % (length - 2));

        do {
          index -= probe;
          if (index < 0) {
            index += length;
          }
          state = hashGetState(index);
          ++total;
          if (total > length) {
            // violation of Euler's theorem
            throw new IllegalStateException("Index corrupted");
          }
        }
        while (state != HASH_FREE &&
               (state == HASH_REMOVED || keyAt(index) != value));
      }

      btree.maxStepsSearchedInHash = Math.max(btree.maxStepsSearchedInHash, total);
      btree.totalHashStepsSearched += total;

      return state == HASH_FREE ? -1 : index;
    }

    protected int hashInsertionIndex(int val) {
      int hash, probe, index;

      final int length = btree.hashPageCapacity;
      hash = hash(val) & 0x7fffffff;
      index = hash % length;
      btree.hashSearchRequests++;

      int state = hashGetState(index);
      if (state == HASH_FREE) {
        return index;       // empty, all done
      }
      else if (state == HASH_FULL && keyAt(index) == val) {
        return -index - 1;   // already stored
      }
      else {                // already FULL or REMOVED, must probe
        // compute the double hash
        probe = 1 + (hash % (length - 2));
        int total = 0;

        // starting at the natural offset, probe until we find an
        // offset that isn't full.
        do {
          index -= probe;
          if (index < 0) {
            index += length;
          }

          ++total;
          state = hashGetState(index);
        }
        while (state == HASH_FULL && keyAt(index) != val);

        // if the index we found was removed: continue probing until we
        // locate a free location or an element which equal()s the
        // one we have.
        if (state == HASH_REMOVED) {
          int firstRemoved = index;
          while (state != HASH_FREE &&
                 (state == HASH_REMOVED || keyAt(index) != val)) {
            index -= probe;
            if (index < 0) {
              index += length;
            }
            state = hashGetState(index);
            ++total;
          }
          return state == HASH_FULL ? -index - 1 : firstRemoved;
        }

        btree.maxStepsSearchedInHash = Math.max(btree.maxStepsSearchedInHash, total);
        btree.totalHashStepsSearched += total;

        // if it's full, the key is already stored
        return state == HASH_FULL ? -index - 1 : index;
      }
    }

    private final int hash(int val) {
      //return val * 0x278DDE6D;
      return val;
    }

    static final int STATE_MASK = 0x3;
    static final int STATE_MASK_WITHOUT_DELETE = 0x1;
    
    private final int hashGetState(int index) {
      byte b = myBuffer.get(hashOccupiedStatusByteOffset(index));
      if (haveDeleteState) {
        return ((b & 0xFF) >> hashOccupiedStatusShift(index)) & STATE_MASK;
      } else {
        return ((b & 0xFF) >> hashOccupiedStatusShift(index)) & STATE_MASK_WITHOUT_DELETE;
      }
    }

    private final int hashOccupiedStatusShift(int index) {
      if (haveDeleteState) {
        return 6 - ((index & 0x3) << 1);
      } else {
        return 7 - (index & 0x7);
      }
    }

    private final int hashOccupiedStatusByteOffset(int index) {
      if (haveDeleteState) {
        return myAddressInBuffer + BtreePage.RESERVED_META_PAGE_LEN + (index >> 2);
      } else {
        return myAddressInBuffer + BtreePage.RESERVED_META_PAGE_LEN + (index >> 3);
      }
    }

    private static final boolean haveDeleteState = false;

    private void hashSetState(int index, int value) {
      if (doSanityCheck) {
        if (haveDeleteState) myAssert(value >= HASH_FREE && value <= HASH_REMOVED);
        else myAssert(value >= HASH_FREE && value < HASH_REMOVED);
      }

      int hashOccupiedStatusOffset = hashOccupiedStatusByteOffset(index);
      byte b = myBuffer.get(hashOccupiedStatusOffset);
      int shift = hashOccupiedStatusShift(index);

      if (haveDeleteState) {
        b = (byte)(((b & 0xFF) & ~(STATE_MASK << shift)) | (value << shift));
      } else {
        b = (byte)(((b & 0xFF) & ~(STATE_MASK_WITHOUT_DELETE << shift)) | (value << shift));
      }
      myBuffer.put(hashOccupiedStatusOffset, b);

      if (doSanityCheck) myAssert(hashGetState(index) == value);
    }
  }
}
