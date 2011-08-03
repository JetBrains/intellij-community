package com.intellij.util.io;

import gnu.trove.TIntIntHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
* Created by IntelliJ IDEA.
* User: maximmossienko
* Date: 7/12/11
* Time: 1:34 PM
*/
abstract class IntToIntBtree {
  static final int VERSION = 1;
  static final boolean doSanityCheck = false;
  static final boolean doDump = false;
  private static final int ROUND_FACTOR = 1048576;

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
  private int nextFreePage = -1;
  private int nextFreePageCount = 0;
  private int count;
  private int movedMembersCount;

  private final byte[] buffer;
  private boolean isLarge = true;
  private final ISimpleStorage storage;
  private final boolean offloadToSiblingsBeforeSplit = false;
  private boolean indexNodeIsHashTable = true;
  final int metaDataLeafPageLength;
  final int hashPageCapacity;

  public IntToIntBtree(int _pageSize, int rootAddress, ISimpleStorage _storage, boolean initial) {
    pageSize = _pageSize;
    buffer = new byte[_pageSize];
    storage = _storage;

    root = new BtreeIndexNodeView(this);
    root.setAddress(rootAddress);

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

    pagesCount = 1;
    ((BtreePage)root).load();

    if (initial) {
      root.setIndexLeaf(true);
      root.sync();
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
    nextFreePage = storage.persistInt(28, nextFreePage, toDisk);
    nextFreePageCount = storage.persistInt(32, nextFreePageCount, toDisk);
    hashedPagesCount = storage.persistInt(36, hashedPagesCount, toDisk);
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

  protected abstract int allocEmptyPage();

  private int nextPage(boolean root) {
    int address;

    if (nextFreePageCount == 0) {
      if ((height == 2 && root) || height >= 3) {  // ensure more locality for index pages
        nextFreePage = allocEmptyPage();
        nextFreePageCount = 1;
        int freePage = nextFreePage + pageSize;
        int numOfFullPages = 1;

        while(freePage % ROUND_FACTOR != 0 || numOfFullPages-- > 0) {
          freePage = allocEmptyPage();
          ++nextFreePageCount;
        }
      }
    }

    if (nextFreePageCount > 0) {
      address = nextFreePage;
      nextFreePage += pageSize;
      --nextFreePageCount;
    } else {
      address = allocEmptyPage();
    }
    ++pagesCount;
    return address;
  }

  public @Nullable Integer get(int key) {
    BtreeIndexNodeView currentIndexNode = new BtreeIndexNodeView(this);
    currentIndexNode.setAddress(root.address);
    int index = currentIndexNode.locate(key, false);

    if (index < 0) return null;
    return currentIndexNode.addressAt(index);
  }

  public void put(int key, int value) {
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

  public int remove(int key) {
    // TODO
    //BtreeIndexNodeView currentIndexNode = new BtreeIndexNodeView(this);
    //currentIndexNode.setAddress(root.address);
    //int index = currentIndexNode.locate(key, false);
    myAssert(BtreeIndexNodeView.haveDeleteState);
    throw new UnsupportedOperationException("Remove does not work yet "+key);
  }

  void setRootAddress(int newRootAddress) {
    root.setAddress(newRootAddress);
  }

  void dumpStatistics() {
    int leafPages = height == 3 ? pagesCount - (1 + root.getChildrenCount() + 1):height == 2 ? pagesCount - 1:1;
    long leafNodesCapacity = hashedPagesCount * maxLeafNodesInHash + (leafPages - hashedPagesCount)* maxLeafNodes;
    long leafNodesCapacity2 = leafPages * maxLeafNodes;
    int usedPercent = (int)((count * 100L) / leafNodesCapacity);
    int usedPercent2 = (int)((count * 100L) / leafNodesCapacity2);
    IOStatistics.dump("pagecount:" + pagesCount + ", height:" + height + ", movedMembers:"+movedMembersCount +
                      ", hash steps:" + maxStepsSearchedInHash + ", avg search in hash:" + (hashSearchRequests != 0 ? totalHashStepsSearched / hashSearchRequests:0) +
                      ", leaf pages used:" + usedPercent + "%, leaf pages used if max children: " + usedPercent2 + "%" );
  }

  void doFlush() {
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
    protected int address;
    private short myChildrenCount;

    public BtreePage(IntToIntBtree btree) {
      this.btree = btree;
      myChildrenCount = -1;
    }

    void setAddress(int _address) {
      if (doSanityCheck) myAssert(_address % btree.pageSize == 0);
      address = _address;
      myChildrenCount = -1;
    }

    protected final boolean getFlag(int mask) {
      return (btree.storage.get(address) & mask) == mask;
    }

    protected final void setFlag(int mask, boolean flag) {
      byte b = btree.storage.get(address);
      if (flag) b |= mask;
      else b &= ~mask;
      btree.storage.put(address, b);
    }

    protected final short getChildrenCount() {
      if (myChildrenCount == -1) {
        myChildrenCount = (short)(((btree.storage.get(address + 1) & 0xFF) << 8) + (btree.storage.get(address + 2) & 0xFF));
      }
      return myChildrenCount;
    }

    protected final void setChildrenCount(short value) {
      myChildrenCount = value;
      btree.storage.put(address + 1, (byte)((value >> 8) & 0xFF));
      btree.storage.put(address + 2, (byte)(value & 0xFF));
    }

    protected final void setNextPage(int nextPage) {
      putInt(address + 3, nextPage);
    }

    // TODO: use it
    protected final int getNextPage() {
      return getInt(address + 3);
    }

    protected final int getInt(int address) {
      return btree.storage.getInt(address);
    }

    protected final void putInt(int offset, int value) {
      btree.storage.putInt(offset, value);
    }

    protected final void getBytes(int address, byte[] dst, int offset, int length) {
      btree.storage.get(address, dst, offset, length);
    }

    private void load() {
    }

    protected final void putBytes(int address, byte[] src, int offset, int length) {
      btree.storage.put(address, src, offset, length);
    }

    void sync() {
    }
}

  // Leaf index node
  // (value_address {<0 if address in duplicates segment}, hash key) {getChildrenCount()}
  // (|next_node {<0} , hash key|) {getChildrenCount()} , next_node {<0}
  // next_node[i] is pointer to all less than hash_key[i] except for the last
  static class BtreeIndexNodeView extends BtreePage {
    static final int INTERIOR_SIZE = 8;
    static final int KEY_OFFSET = 4;

    private boolean isIndexLeaf;
    private boolean isIndexLeafSet;
    private boolean isHashedLeaf;
    private boolean isHashedLeafSet;
    private static final int LARGE_MOVE_THRESHOLD = 5;

    BtreeIndexNodeView(IntToIntBtree btree) {
      super(btree);
    }

    @Override
    void setAddress(int _address) {
      super.setAddress(_address);
      isIndexLeafSet = false;
      isHashedLeafSet = false;
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
        myAssert(offset + 4 <= address + btree.pageSize);
        myAssert(offset >= address + metaPageLen);
      }
      putInt(offset, value);
    }

    private final int indexToOffset(int i) {
      return address + i * INTERIOR_SIZE + (isHashedLeaf() ? btree.metaDataLeafPageLength:RESERVED_META_PAGE_LEN);
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
        myAssert(offset + 4 <= address + btree.pageSize);
        myAssert(offset >= address + metaPageLen);
      }
      putInt(offset, value);
    }

    static final int INDEX_LEAF_MASK = 0x1;
    static final int HASHED_LEAF_MASK = 0x2;

    final boolean isIndexLeaf() {
      if (!isIndexLeafSet) {
        isIndexLeaf = getFlag(INDEX_LEAF_MASK);
        isIndexLeafSet = true;
      }
      return isIndexLeaf;
    }

    void setIndexLeaf(boolean value) {
      isIndexLeaf = value;
      setFlag(INDEX_LEAF_MASK, value);
    }

    final boolean isHashedLeaf() {
      if (!isHashedLeafSet) {
        isHashedLeaf = getFlag(HASHED_LEAF_MASK);
        isHashedLeafSet = true;
      }
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
        getBytes(indexToOffset(0), btree.buffer, 0, btree.pageSize - btree.metaDataLeafPageLength);
        int keyNumber = 0;

        for(int i = 0; i < btree.hashPageCapacity; ++i) {
          if (hashGetState(i) == HASH_FULL) {
            int key = Bits.getInt(btree.buffer, i * INTERIOR_SIZE + KEY_OFFSET);
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
    
    private int splitNode(int parentAddress) {
      if (doSanityCheck) {
        myAssert(isFull());
        dump("before split:"+isIndexLeaf());
      }

      BtreeIndexNodeView parent = null;

      if (parentAddress != 0) {
        parent = new BtreeIndexNodeView(btree);
        parent.setAddress(parentAddress);
        if (btree.offloadToSiblingsBeforeSplit) {
          if (doOffloadToSiblingsSorted(parent)) return parentAddress;
        }
      }

      short maxIndex = (short)(getMaxChildrenCount() / 2);

      BtreeIndexNodeView newIndexNode = new BtreeIndexNodeView(btree);
      newIndexNode.setAddress(btree.nextPage(false));

      boolean indexLeaf = isIndexLeaf();
      newIndexNode.setIndexLeaf(indexLeaf);

      int nextPage = getNextPage();
      setNextPage(newIndexNode.address);
      newIndexNode.setNextPage(nextPage);

      final short recordCount = getChildrenCount();

      int medianKey;

      if (indexLeaf && isHashedLeaf()) {
        TIntIntHashMap map = new TIntIntHashMap(recordCount);
        getBytes(indexToOffset(0), btree.buffer, 0, btree.pageSize - btree.metaDataLeafPageLength);
        int[] keys = new int[recordCount];
        int keyNumber = 0;
        
        for(int i = 0; i < btree.hashPageCapacity; ++i) {
          if (hashGetState(i) == HASH_FULL) {
            int key = Bits.getInt(btree.buffer, i * INTERIOR_SIZE + KEY_OFFSET);
            keys[keyNumber++] = key;
            map.put(key, Bits.getInt(btree.buffer, i * INTERIOR_SIZE));
            hashSetState(i, HASH_FREE);
          }
        }

        Arrays.sort(keys);
        final int avg = keys.length / 2;
        medianKey = keys[avg];
        --btree.hashedPagesCount;
        setChildrenCount((short)0);
        newIndexNode.setChildrenCount((short)0);

        for(int i = 0; i < avg; ++i) {
          insert(keys[i], map.get(keys[i]));
          newIndexNode.insert(keys[avg + i], map.get(keys[avg + i]));
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
      } else {
        short recordCountInNewNode = (short)(recordCount - maxIndex);
        newIndexNode.setChildrenCount(recordCountInNewNode);

        if (btree.isLarge) {
          final int bytesToMove = recordCountInNewNode * INTERIOR_SIZE;
          getBytes(indexToOffset(maxIndex), btree.buffer, 0, bytesToMove);
          newIndexNode.putBytes(newIndexNode.indexToOffset(0), btree.buffer, 0, bytesToMove);
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
        int newRootAddress = btree.nextPage(true);
        if (doSanityCheck) {
          System.out.println("Pages:"+btree.pagesCount+", elements:"+btree.count + ", average:" + (btree.height + 1));
        }
        btree.setRootAddress(newRootAddress);
        parentAddress = newRootAddress;
        ((BtreePage)btree.root).load();
        btree.root.setChildrenCount((short)1);
        btree.root.setKeyAt(0, medianKey);
        btree.root.setAddressAt(0, -address);
        btree.root.setAddressAt(1, -newIndexNode.address);
        btree.root.sync();

        if (doSanityCheck) {
          btree.root.dump("New root");
          dump("First child");
          newIndexNode.dump("Second child");
        }
      }

      sync();
      newIndexNode.sync();

      return parentAddress;
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
            final int bytesToMove = indexOfLastChildToMove * INTERIOR_SIZE;
            getBytes(indexToOffset(toMove), btree.buffer, 0, bytesToMove);
            putBytes(indexToOffset(0), btree.buffer, 0, bytesToMove);
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
        sync();
        parent.sync();

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
        if (doSanityCheck) myAssert(address != 0);
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
          sync();
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
          final int bytesToMove = itemsToMove * INTERIOR_SIZE;
          getBytes(indexToOffset(index), btree.buffer, 0, bytesToMove);
          putBytes(indexToOffset(index + 1), btree.buffer, 0, bytesToMove);
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
            int bytesToMove = elementsAfterIndex * INTERIOR_SIZE;
            getBytes(indexToOffset(index + 1), btree.buffer, 0, bytesToMove);
            putBytes(indexToOffset(index + 2), btree.buffer, 0, bytesToMove);
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

      sync();
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
      return val * 0x278DDE6D;
    }

    static final int STATE_MASK = 0x3;
    static final int STATE_MASK_WITHOUT_DELETE = 0x1;
    
    private final int hashGetState(int index) {
      byte b = btree.storage.get(hashOccupiedStatusByteOffset(index));
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
        return address + BtreePage.RESERVED_META_PAGE_LEN + (index >> 2);
      } else {
        return address + BtreePage.RESERVED_META_PAGE_LEN + (index >> 3);
      }
    }

    private static final boolean haveDeleteState = false;

    private void hashSetState(int index, int value) {
      if (doSanityCheck) {
        if (haveDeleteState) myAssert(value >= HASH_FREE && value <= HASH_REMOVED);
        else myAssert(value >= HASH_FREE && value < HASH_REMOVED);
      }

      int hashOccupiedStatusOffset = hashOccupiedStatusByteOffset(index);
      byte b = btree.storage.get(hashOccupiedStatusOffset);
      int shift = hashOccupiedStatusShift(index);

      if (haveDeleteState) {
        b = (byte)(((b & 0xFF) & ~(STATE_MASK << shift)) | (value << shift));
      } else {
        b = (byte)(((b & 0xFF) & ~(STATE_MASK_WITHOUT_DELETE << shift)) | (value << shift));
      }
      btree.storage.put(hashOccupiedStatusOffset, b);

      if (doSanityCheck) myAssert(hashGetState(index) == value);
    }
  }
}
