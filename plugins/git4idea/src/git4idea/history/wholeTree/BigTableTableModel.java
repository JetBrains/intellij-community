/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea.history.wholeTree;

import com.intellij.openapi.diff.impl.patch.formove.FilePathComparator;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.BigArray;
import com.intellij.openapi.vcs.GroupingMerger;
import com.intellij.openapi.vcs.changes.committed.DateChangeListGroupingStrategy;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.*;
import com.intellij.util.ui.ColumnInfo;
import git4idea.history.browser.GitHeavyCommit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.AbstractTableModel;
import java.util.*;
import java.util.HashMap;
import java.util.HashSet;

/**
 * @author irengrig
 */
public class BigTableTableModel extends AbstractTableModel {
  public final static Object LOADING = new Object();
  public static final String STASH = "Stash";
  // should be grouped
  @Nullable
  private Map<VirtualFile, SkeletonBuilder> mySkeletonBuilder;
  @Nullable
  private Map<VirtualFile, TreeNavigationImpl> myNavigation;
  @Nullable
  private List<VirtualFile> myOrder;
  private Map<VirtualFile, Integer> myAdditions;
  // end group
  
  private final BidirectionalMap<InnerIdx, Integer> myIdxMap;
  // for faster drawing
  private final Map<VirtualFile, TreeSet<Integer>> myRepoIdxMap;
  // index of NEXT
  private final Map<VirtualFile, Integer> myRunningRepoIdxs;

  @NotNull
  private final List<ColumnInfo> myColumns;
  private RootsHolder myRootsHolder;
  @Nullable
  private StepList<CommitI> myLines;
  private int myCutCount;
  private DetailsCache myCache;
  private Runnable myInit;
  private CommitGroupingStrategy myStrategy;
  private Comparator<CommitI> myCurrentComparator;
  
  private int myCommitIdxInterval;
  private int myNumEventsInGroup;
  
  private final Set<VirtualFile> myActiveRoots;
  private final CommitGroupingStrategy myDefaultStrategy;
  private final CommitGroupingStrategy myNoGrouping;
  private final Map<VirtualFile,Pair<AbstractHash, AbstractHash>> myStashTops;
  
  private final Map<VirtualFile, TreeHighlighter> myTreeHighlighter;

  public BigTableTableModel(@NotNull final List<ColumnInfo> columns, Runnable init) {
    myColumns = columns;
    myInit = init;
    myIdxMap = new BidirectionalMap<InnerIdx, Integer>();
    myRunningRepoIdxs = new HashMap<VirtualFile, Integer>();
    myRepoIdxMap = new HashMap<VirtualFile, TreeSet<Integer>>();
    myActiveRoots = new HashSet<VirtualFile>();

    myCurrentComparator = CommitIReorderingInsideOneRepoComparator.getInstance();
    final DateChangeListGroupingStrategy delegate = new DateChangeListGroupingStrategy();
    myDefaultStrategy = new CommitGroupingStrategy() {
      @Override
      public void beforeStart() {
        delegate.beforeStart();
      }

      @Override
      public String getGroupName(CommitI commit) {
        return delegate.getGroupName(new Date(commit.getTime()));
      }
    };
    myStrategy = myDefaultStrategy;
    myLines = new BigArray<CommitI>(10);
    myCutCount = -1;

    myCommitIdxInterval = 50;
    myNumEventsInGroup = 20;
    myNoGrouping = new CommitGroupingStrategy() {
      @Override
      public String getGroupName(CommitI commit) {
        return null;
      }

      @Override
      public void beforeStart() {
      }
    };
    myStashTops = new HashMap<VirtualFile, Pair<AbstractHash, AbstractHash>>();
    myTreeHighlighter = new HashMap<VirtualFile, TreeHighlighter>();
  }
  
  public boolean isForRoot(final VirtualFile root, final int idx) {
    return myRepoIdxMap.get(root).contains(idx);
  }
  
  public int getAbsoluteForRelative(final VirtualFile root, final int insideRepoIdx) {
    return myIdxMap.get(new InnerIdx(root, insideRepoIdx));
  }
  
  public int getPreviousAbsoluteIdx(final int idx) {
    final VirtualFile root = getCommitAt(idx).selectRepository(myRootsHolder.getRoots());
    final List<InnerIdx> keysByValue = myIdxMap.getKeysByValue(idx);
    if (keysByValue == null) {
      // this is for group header
      return -1;
    } else {
      for (InnerIdx innerIdx : keysByValue) {
        if (innerIdx.getRoot().equals(root)) {
          if (innerIdx.getInsideRepoIdx() == 0) return -1;
          return myIdxMap.get(new InnerIdx(root, innerIdx.getInsideRepoIdx() - 1));
        }
      }
    }
    //assert false;
    return -1;
  }

  public void setCommitIdxInterval(int commitIdxInterval) {
    myCommitIdxInterval = commitIdxInterval;
  }

  public void setNumEventsInGroup(int numEventsInGroup) {
    myNumEventsInGroup = numEventsInGroup;
  }

  public ColumnInfo getColumnInfo(final int column) {
    return myColumns.get(column);
  }

  @Override
  public String getColumnName(int column) {
    return myColumns.get(column).getName();
  }

  @Override
  public int getColumnCount() {
    return myColumns.size();
  }

  int getTrueCount() {
    return myLines == null ? 0 : myLines.getSize();
  }

  @Override
  public int getRowCount() {
    if (myInit != null) {
      final Runnable init = myInit;
      myInit = null;
      init.run();
    }
    if (myCutCount > 0) {
      return myCutCount;
    }
    return myLines == null ? 0 : myLines.getSize();
  }

  public CommitI getCommitAt(final int row) {
    if (myLines == null) return null;
    if (row >= myLines.getSize()) return null;
    return myLines.get(row);
  }
  
  public int getTotalWires() {
    if (mySkeletonBuilder == null) return -1;
    int wires = 0;
    for (Map.Entry<VirtualFile, SkeletonBuilder> entry : mySkeletonBuilder.entrySet()) {
      SkeletonBuilder skeletonBuilder = entry.getValue();
      if (myActiveRoots.contains(entry.getKey())) {
        wires += skeletonBuilder.getMaxWireNum();
      }
    }
    return wires;
  }
  
  @Nullable
  public List<Integer> getWiresGroups() {
    if (mySkeletonBuilder == null) return null;
    final List<Integer> result = new ArrayList<Integer>(myOrder.size());
    for (VirtualFile file : myOrder) {
      if (myActiveRoots.contains(file)) {
        result.add(mySkeletonBuilder.get(file).getMaxWireNum());
      }
    }
    return result;
  }
  
  public int getCorrectedWire(final CommitI commitI) {
    if (mySkeletonBuilder == null) return -1;
    final VirtualFile file = commitI.selectRepository(myRootsHolder.getRoots());
    if (! myAdditions.containsKey(file)) return commitI.getWireNumber();
    return myAdditions.get(file) + commitI.getWireNumber();
  }

  public int getRepoCorrection(final VirtualFile root) {
    return myAdditions.get(root);
  }
  
  public Map<VirtualFile, WireEventsIterator> getGroupIterators(final int firstRow) {
    final Map<VirtualFile, WireEventsIterator> map = new HashMap<VirtualFile, WireEventsIterator>();
    for (VirtualFile virtualFile : mySkeletonBuilder.keySet()) {
      if (myActiveRoots.contains(virtualFile)) {
        map.put(virtualFile, new WiresGroupIterator(firstRow, virtualFile));
      }
    }
    return map;
  }

  // todo should be removed once we move to inside-repository indexes calculation
  public Map<VirtualFile, WireEventsIterator> getAllGroupIterators(final int firstRow) {
    final Map<VirtualFile, WireEventsIterator> map = new HashMap<VirtualFile, WireEventsIterator>();
    for (VirtualFile virtualFile : mySkeletonBuilder.keySet()) {
      map.put(virtualFile, new WiresGroupIterator(firstRow, virtualFile));
    }
    return map;
  }

  public void stashFor(VirtualFile root, Pair<AbstractHash, AbstractHash> hash) {
    myStashTops.put(root, hash);
  }

  public boolean isStashed(final CommitI commitI) {
    final Pair<AbstractHash, AbstractHash> pair =
      myStashTops.get(commitI.selectRepository(myRootsHolder.getRoots()));
    return pair != null && (pair.getFirst() != null && pair.getFirst().equals(commitI.getHash()) ||
                            pair.getSecond() != null && pair.getSecond().equals(commitI.getHash()));
  }

  public void setHeadIfEmpty(VirtualFile root, AbstractHash headHash) {
    final TreeHighlighter treeHighlighter = myTreeHighlighter.get(root);
    if (treeHighlighter != null && treeHighlighter.getPoint() == null) {
      setHead(root, headHash);
    }
  }

  public void setHead(VirtualFile root, AbstractHash headHash) {
    final TreeHighlighter treeHighlighter = myTreeHighlighter.get(root);
    if (treeHighlighter != null) {
      if (! treeHighlighter.isDumb() && headHash.equals(treeHighlighter.getPoint())) return;  // already ok
      treeHighlighter.setPoint(headHash);
      treeHighlighter.update(0);
    }
  }

  public void setDumbHighlighter(VirtualFile root) {
    final TreeHighlighter treeHighlighter = myTreeHighlighter.get(root);
    if (treeHighlighter != null) {
      if (treeHighlighter.isDumb()) return;
      treeHighlighter.setDumb();
    }
  }
  
  /*private static class IdxPair {
    public int myReal;
    public int myInside;

    private IdxPair(int real, int inside) {
      myReal = real;
      myInside = inside;
    }
  }
  
  private IdxPair getLess(final int x, final VirtualFile root) {
    int idx = x;
    while (idx >= 0) {
      final List<InnerIdx> keysByValue = myIdxMap.getKeysByValue(idx);
      if (keysByValue != null) {
        for (InnerIdx innerIdx : keysByValue) {
          if (innerIdx.getRoot().equals(root)) {
            return new IdxPair(idx, innerIdx.getInsideRepoIdx());
          }
        }
      }
      -- idx;
    }
    return new IdxPair(0,0);
  }*/

  class WiresGroupIterator implements WireEventsIterator {
    private final int myFirstIdxAbs;
    private List<Integer> myFirstUsed;
    private final VirtualFile myRoot;
    private final int myOffset;
    private final Iterator<WireEvent> myWireEventsIterator;
    private Integer myFloor;
    private int myIdx;

    WiresGroupIterator(int firstIdxAbs, VirtualFile root) {
      myFirstIdxAbs = firstIdxAbs;
      myRoot = root;
      myOffset = myAdditions.containsKey(myRoot) ? myAdditions.get(myRoot) : 0;

      myFirstUsed = new ArrayList<Integer>();
      TreeNavigationImpl navigation = myNavigation.get(myRoot);

      // get less idx
      /*final IdxPair less = getLess(firstIdxAbs, root);
      myFloor = less.myReal;
      myIdx = less.myInside;*/

      final ReadonlyList<CommitI> wrapper = createWrapper(myRoot);
      myFloor = myRepoIdxMap.get(myRoot).floor(firstIdxAbs);
      if (myFloor == null) {
        myIdx = 0;
        myFloor = myRepoIdxMap.get(myRoot).isEmpty() ? 0 : myRepoIdxMap.get(myRoot).first();
      } else {
        final List<InnerIdx> keysByValue = myIdxMap.getKeysByValue(myFloor);
        myIdx = -1;
        for (InnerIdx innerIdx : keysByValue) {
          if (innerIdx.getRoot().equals(myRoot)) {
            myIdx = innerIdx.getInsideRepoIdx();
            break;
          }
        }
        assert myIdx != -1;
      }
      final List<Integer> used = navigation.getUsedWires(myIdx, wrapper, mySkeletonBuilder.get(myRoot).getFutureConvertor()).getUsed();
      for (Integer integer : used) {
        myFirstUsed.add(integer + myOffset);
      }
      myWireEventsIterator = navigation.createWireEventsIterator(myIdx);
    }

    @Override
    public Integer getFloor() {
      return myFloor;
    }

    @Override
    public Iterator<WireEventI> getWireEventsIterator() {
      return new Iterator<WireEventI>() {
        @Override
        public boolean hasNext() {
          return myWireEventsIterator.hasNext();
        }

        @Override
        public WireEventI next() {
          final WireEventI next = myWireEventsIterator.next();
          final Convertor<Integer, Integer> innerToOuter = new Convertor<Integer, Integer>() {
            @Override
            public Integer convert(Integer o) {
              if (o == -1) return -1;
              final int insideRepoIdx = o.intValue();
              final Integer integer = myIdxMap.get(new InnerIdx(myRoot, insideRepoIdx));
              assert integer != null;
              return integer;
            }
          };
          final Convertor<int[], int[]> arraysConvertor = new Convertor<int[], int[]>() {
            @Override
            public int[] convert(int[] o) {
              if (o == null) return null;
              final int[] result = new int[o.length];
              for (int i = 0; i < o.length; i++) {
                int i1 = o[i];
                result[i] = innerToOuter.convert(i1);
              }
              return result;
            }
          };
          return new WireEventI() {
            @Override
            public int getCommitIdx() {
              return innerToOuter.convert(next.getCommitIdx());
            }

            @Override
            public int[] getWireEnds() {
              return arraysConvertor.convert(next.getWireEnds());
            }

            @Override
            public int[] getCommitsEnds() {
              return arraysConvertor.convert(next.getCommitsEnds());
            }

            @Override
            public int[] getCommitsStarts() {
              return arraysConvertor.convert(next.getCommitsStarts());
            }

            @Override
            public int[] getFutureWireStarts() {
              // these are wire numbers
              return next.getFutureWireStarts();
            }

            @Override
            public boolean isEnd() {
              return next.isEnd();
            }

            @Override
            public boolean isStart() {
              return next.isStart();
            }

            @Override
            public int getWaitStartsNumber() {
              return next.getWaitStartsNumber();
            }
          };
        }

        @Override
        public void remove() {
          throw new UnsupportedOperationException();
        }
      };
    }

    @Override
    public List<Integer> getFirstUsed() {
      return myFirstUsed;
    }
  }
  
  public int getLastForRoot(final VirtualFile root) {
    final TreeSet<Integer> integers = myRepoIdxMap.get(root);
    if (integers.isEmpty()) return -1;
    return integers.last();
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    final ColumnInfo column = myColumns.get(columnIndex);
    if (myLines == null) return column.getPreferredStringValue();
    final CommitI commitI = myLines.get(rowIndex);
    if (commitI == null) return column.getPreferredStringValue();
    if (commitI.holdsDecoration()) return columnIndex == 0 ? commitI.getDecorationString() : "";

    final GitHeavyCommit details = myCache.convert(commitI.selectRepository(myRootsHolder.getRoots()), commitI.getHash());
    if (details == null) return LOADING;
    return column.valueOf(details);
  }

  public MultiMap<VirtualFile, AbstractHash> getMissing(final int startRow, final int endRow) {
    if (myLines == null || myRootsHolder == null) return MultiMap.emptyInstance();
    final MultiMap<VirtualFile, AbstractHash> result = new MultiMap<VirtualFile, AbstractHash>();
    for (int i = startRow; i <= endRow; i++) {
      final CommitI commitI = myLines.get(i);
      if (commitI.holdsDecoration()) continue;

      final AbstractHash hash = commitI.getHash();
      final VirtualFile root = commitI.selectRepository(myRootsHolder.getRoots());
      if (myCache.convert(root, commitI.getHash()) == null) {
        result.putValue(root, hash);
      }
    }
    return result;
  }

  public void clear(boolean noFilters, boolean noStartingPoints) {
    if (noFilters) {
      myCurrentComparator = CommitIComparator.getInstance();
      myNavigation = new HashMap<VirtualFile, TreeNavigationImpl>();
      mySkeletonBuilder = new HashMap<VirtualFile, SkeletonBuilder>();
      myAdditions = new HashMap<VirtualFile, Integer>();
      myOrder = new ArrayList<VirtualFile>(myRootsHolder.getRoots());
      Collections.sort(myOrder, FilePathComparator.getInstance());
      for (VirtualFile vf : myOrder) {
        final TreeNavigationImpl navigation = new TreeNavigationImpl(myCommitIdxInterval, myNumEventsInGroup);// try to adjust numbers
        final SkeletonBuilder skeletonBuilder = new SkeletonBuilder(navigation);
        myNavigation.put(vf, navigation);
        mySkeletonBuilder.put(vf, skeletonBuilder);
        myAdditions.put(vf, 0);
        myRepoIdxMap.put(vf, new TreeSet<Integer>());

        myRunningRepoIdxs.put(vf, 0);
        myIdxMap.clear();
      }
      for (VirtualFile virtualFile : myOrder) {
        if (! myTreeHighlighter.containsKey(virtualFile)) {
          myTreeHighlighter.put(virtualFile, new TreeHighlighter(this, virtualFile, -1));
        }
      }
      for (Iterator<VirtualFile> iterator = myTreeHighlighter.keySet().iterator(); iterator.hasNext(); ) {
        VirtualFile root = iterator.next();
        if (! myOrder.contains(root)) {
          iterator.remove();
        } else {
          final TreeHighlighter highlighter = myTreeHighlighter.get(root);
          highlighter.setPoint(highlighter.getPoint());
        }
      }
    } else {
      myCurrentComparator = CommitIReorderingInsideOneRepoComparator.getInstance();

      myAdditions = null;
      mySkeletonBuilder = null;
      myNavigation = null;
      myOrder = null;
      myTreeHighlighter.clear();
    }
    myLines = new BigArray<CommitI>(10);
    myCutCount = -1;
  }

  @Nullable
  public List<VirtualFile> getOrder() {
    return myOrder;
  }

  public void cutAt(final int lastShownItemIdx) {
    myCutCount = lastShownItemIdx + 1;
  }

  public void restore() {
    myCutCount = -1;
  }

  public void appendData(final List<CommitI> lines, final List<List<AbstractHash>> parents) {
    if (mySkeletonBuilder == null) {
      Collections.sort(lines, myCurrentComparator);
    }

    myStrategy.beforeStart();
    
    // find those ..... long awaited start idx by stupid long iteration since
    // items can NOT be ordered by simple rule
    final int[] parentsIdx = new int[1];
    parentsIdx[0] = 0;
    int idxFrom = findIdx(lines);

    final CommitI commitI = lines.get(0);
    final VirtualFile listRoot = commitI.selectRepository(myRootsHolder.getRoots());
    final ReadonlyList<CommitI> wrapperList = createWrapper(listRoot);

    int recountFrom = new GroupingMerger<CommitI, String>() {
      @Override
      protected CommitI wrapItem(CommitI commitI) {
        if (mySkeletonBuilder != null && ! commitI.holdsDecoration()) {
          return new WireNumberCommitDecoration(commitI);
        }
        return super.wrapItem(commitI);
      }

      @Override
      protected void afterConsumed(CommitI commitI, int i) {
        if (mySkeletonBuilder != null && ! commitI.holdsDecoration()) {
          final VirtualFile root = commitI.selectRepository(myRootsHolder.getRoots());
          final Integer innerIdx = myRunningRepoIdxs.get(root);
          myIdxMap.put(new InnerIdx(root, innerIdx), i);
          myRepoIdxMap.get(root).add(i);
          myRunningRepoIdxs.put(root, innerIdx + 1);

          mySkeletonBuilder.get(root).consume(commitI, parents.get(parentsIdx[0]), wrapperList, innerIdx);
          ++ parentsIdx[0];
        }
      }

      @Override
      protected boolean filter(CommitI commitI) {
        return !commitI.holdsDecoration();
      }

      @Override
      protected void willBeRecountFrom(int idx, int wasSize) {
        if (mySkeletonBuilder != null) {
          for (int i = idx; i < wasSize; i++) {
            //myIdxMap.removeValue(i);
            for (VirtualFile root : myOrder) {
              myRepoIdxMap.get(root).remove(i);
            }
          }
        }
      }

      @Override
      protected String getGroup(CommitI commitI) {
        if (getCurrentGroup() == null) {
          final Pair<AbstractHash, AbstractHash> stashTop = myStashTops.get(commitI.selectRepository(myRootsHolder.getRoots()));
          if (stashTop != null && (Comparing.equal(stashTop.getFirst(), commitI.getHash()))) {
            return STASH;
          }
        }
        if (STASH.equals(getCurrentGroup())) { // index on <branchname>: <short hash> <base commit description>
          final VirtualFile root = commitI.selectRepository(myRootsHolder.getRoots());
          final Pair<AbstractHash, AbstractHash> stashTop = myStashTops.get(root);
          if (stashTop != null && (Comparing.equal(stashTop.getSecond(), commitI.getHash()))) {
            return STASH;
          }
        }
        return myStrategy.getGroupName(commitI);
      }

      @Override
      protected CommitI wrapGroup(String s, CommitI item) {
        return new GroupHeaderDatePseudoCommit(s, item.getTime() - 1);
      }

      @Override
      protected void oldBecame(int was, int is) {
        if (mySkeletonBuilder != null) {
          final List<InnerIdx> keys = myIdxMap.getKeysByValue(was);
          final VirtualFile root = myLines.get(is).selectRepository(myRootsHolder.getRoots());
          //final VirtualFile wasRoot = myLines.get(was).selectRepository(myRootsHolder.getRoots());
          assert ! root.equals(listRoot);
          //myRepoIdxMap.get(wasRoot).remove(was);
          myRepoIdxMap.get(root).add(is);

          InnerIdx found = null;
          for (InnerIdx key : keys) {
            if (key.getRoot().equals(root)) {
              found = key;
              break;
            }
          }
          assert found != null;
          myIdxMap.put(found, is);
        }
      }
    }.firstPlusSecond(myLines, new ReadonlyList.ArrayListWrapper<CommitI>(lines), myCurrentComparator, mySkeletonBuilder == null ? -1 : idxFrom);
    
    if (mySkeletonBuilder != null) {
      final TreeNavigationImpl treeNavigation = myNavigation.get(listRoot);
      treeNavigation.recalcIndex(wrapperList, mySkeletonBuilder.get(listRoot).getFutureConvertor());
      calculateAdditions();
      for (VirtualFile root : myOrder) {
        final TreeHighlighter treeHighlighter = myTreeHighlighter.get(root);
        if (treeHighlighter != null) {
          treeHighlighter.update(recountFrom);
        }
      }
    }
  }

  private void calculateAdditions() {
    if (mySkeletonBuilder != null) {
      int size = 0;
      myAdditions.clear();
      for (VirtualFile file : myOrder) {
        if (myActiveRoots.contains(file)) {
          myAdditions.put(file, size);
          size += mySkeletonBuilder.get(file).getMaxWireNum();
        }
      }
    }
  }
  
  public boolean isInCurrentBranch(final int idx) {
    if (mySkeletonBuilder == null || myTreeHighlighter.isEmpty()) return false;
    final CommitI commitAt = getCommitAt(idx);
    if (commitAt.holdsDecoration()) return false;
    final VirtualFile root = commitAt.selectRepository(myRootsHolder.getRoots());
    final TreeHighlighter treeHighlighter = myTreeHighlighter.get(root);
    return treeHighlighter != null && treeHighlighter.isIncluded(commitAt.getHash());
  }
  
  public Map<Integer, Set<Integer>> getGrey(final VirtualFile root,
                                            final int from,
                                            final int to,
                                            int repoCorrection,
                                            Set<Integer> wireModificationSet) {
    if (mySkeletonBuilder == null || myTreeHighlighter.isEmpty()) return Collections.emptyMap();
    final TreeHighlighter highlighter = myTreeHighlighter.get(root);
    return highlighter.getGreyForInterval(from, to, repoCorrection, wireModificationSet);
  }
  
  private ReadonlyList<CommitI> createWrapper(final VirtualFile root) {
    return new ReadonlyList<CommitI>() {
        @Override
        public CommitI get(int idx) {
          return myLines.get(myIdxMap.get(new InnerIdx(root, idx)));
        }

        @Override
        public int getSize() {
          return myRunningRepoIdxs.get(root);
        }
      };
  }

  private int findIdx(List<CommitI> lines) {
    final VirtualFile targetRepo = lines.get(0).selectRepository(myRootsHolder.getRoots());
    final long time = lines.get(0).getTime();

    for (int i = myLines.getSize() - 1; i >= 0; i--) {
      final CommitI current = myLines.get(i);
      if (current.holdsDecoration()) continue;
      if (current.selectRepository(myRootsHolder.getRoots()).equals(targetRepo)) {
        return i + 1;      // will be equal to list size sometimes, is that ok?
      } else {
        if (current.getTime() > time) {
          return i;
        }
      }
    }
    return 0;
  }

  public void setCache(DetailsCache cache) {
    myCache = cache;
  }

  public void setRootsHolder(RootsHolder rootsHolder) {
    myRootsHolder = rootsHolder;
  }

  public RootsHolder getRootsHolder() {
    return myRootsHolder;
  }

  public void setStrategy(CommitGroupingStrategy strategy) {
    myStrategy = strategy;
  }

  public void useDateGroupingStrategy() {
    myStrategy = myDefaultStrategy;
  }

  public void useNoGroupingStrategy() {
    myStrategy = myNoGrouping;
  }

  public void printNavigation() {
    for (Map.Entry<VirtualFile, TreeNavigationImpl> entry : myNavigation.entrySet()) {
      if (entry.getKey().getPath().contains("inner")) {
        entry.getValue().printSelf();
      }
    }
  }

  public static class InnerIdx {
    private final VirtualFile myRoot;
    private final int myInsideRepoIdx;

    public InnerIdx(VirtualFile root, int insideRepoIdx) {
      myRoot = root;
      myInsideRepoIdx = insideRepoIdx;
    }

    public VirtualFile getRoot() {
      return myRoot;
    }

    public int getInsideRepoIdx() {
      return myInsideRepoIdx;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      InnerIdx innerIdx = (InnerIdx)o;

      if (myInsideRepoIdx != innerIdx.myInsideRepoIdx) return false;
      if (!myRoot.equals(innerIdx.myRoot)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myRoot.hashCode();
      result = 31 * result + myInsideRepoIdx;
      return result;
    }
  }

  public Set<VirtualFile> getActiveRoots() {
    return myActiveRoots;
  }
  
  public void setActiveRoots(final Collection<VirtualFile> files) {
    myActiveRoots.clear();
    myActiveRoots.addAll(files);
    for (TreeHighlighter highlighter : myTreeHighlighter.values()) {
      //highlighter.reset();
      highlighter.update(0);
    }

    calculateAdditions();
  }
}
