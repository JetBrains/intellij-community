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
import com.intellij.openapi.vcs.BigArray;
import com.intellij.openapi.vcs.GroupingMerger;
import com.intellij.openapi.vcs.changes.committed.DateChangeListGroupingStrategy;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.ReadonlyList;
import com.intellij.util.containers.StepList;
import com.intellij.util.ui.ColumnInfo;
import git4idea.history.browser.GitCommit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.AbstractTableModel;
import java.util.*;

/**
 * @author irengrig
 */
public class BigTableTableModel extends AbstractTableModel {
  public final static Object LOADING = new Object();
  // should be grouped
  @Nullable
  private Map<VirtualFile, SkeletonBuilder> mySkeletonBuilder;
  @Nullable
  private Map<VirtualFile, TreeNavigationImpl> myNavigation;
  @Nullable
  private List<VirtualFile> myOrder;
  private Map<VirtualFile, Integer> myAdditions;
  // end group

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

  public BigTableTableModel(@NotNull final List<ColumnInfo> columns, Runnable init) {
    myColumns = columns;
    myInit = init;
    myCurrentComparator = CommitIReorderingInsideOneRepoComparator.getInstance();
    final DateChangeListGroupingStrategy delegate = new DateChangeListGroupingStrategy();
    myStrategy = new CommitGroupingStrategy() {
      @Override
      public void beforeStart() {
        delegate.beforeStart();
      }

      @Override
      public String getGroupName(CommitI commit) {
        return delegate.getGroupName(new Date(commit.getTime()));
      }
    };
    myLines = new BigArray<CommitI>(10);
    myCutCount = -1;

    myCommitIdxInterval = 50;
    myNumEventsInGroup = 20;
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
    for (TreeNavigationImpl navigation : myNavigation.values()) {
      wires += navigation.getMaximumWires();
    }
    return wires;
  }
  
  @Nullable
  public List<Integer> getWiresGroups() {
    if (mySkeletonBuilder == null) return null;
    final List<Integer> result = new ArrayList<Integer>(myOrder.size());
    for (VirtualFile file : myOrder) {
      result.add(myNavigation.get(file).getMaximumWires());
    }
    return result;
  }
  
  public int getCorrectedWire(final CommitI commitI) {
    if (mySkeletonBuilder == null) return -1;
    final VirtualFile file = commitI.selectRepository(myRootsHolder.getRoots());
    return myAdditions.get(file) + commitI.getWireNumber();
  }
  
  public WiresGroupIterator getGroupIterator(final int firstRow) {
    return new WiresGroupIterator(firstRow);
  }
  
  class WiresGroupIterator {
    private final int myFirstIdx;
    private List<Integer> myFirstUsed;

    WiresGroupIterator(int firstIdx) {
      myFirstIdx = firstIdx;
      myFirstUsed = new ArrayList<Integer>();
      for (VirtualFile file : myOrder) {
        TreeNavigationImpl navigation = myNavigation.get(file);
        final List<Integer> used = navigation.getUsedWires(firstIdx, myLines, mySkeletonBuilder.get(file).getFutureConvertor()).getUsed();
        myFirstUsed.addAll(used);
      }
    }

    public List<Integer> getFirstUsed() {
      return myFirstUsed;
    }

    public WireEvent getEventForRow(final int row) {
      assert row >= myFirstIdx;
      return myNavigation.get(getCommitAt(row).selectRepository(myRootsHolder.getRoots())).getEventForRow(row);
    }
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    final ColumnInfo column = myColumns.get(columnIndex);
    if (myLines == null) return column.getPreferredStringValue();
    final CommitI commitI = myLines.get(rowIndex);
    if (commitI == null) return column.getPreferredStringValue();
    if (commitI.holdsDecoration()) return columnIndex == 0 ? commitI.getDecorationString() : "";

    final GitCommit details = myCache.convert(commitI.selectRepository(myRootsHolder.getRoots()), commitI.getHash());
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

  public void clear(boolean noFilters) {
    // todo uncomment for git log tree
    /*if (noFilters) {
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
      }
    } else {
      myCurrentComparator = CommitIReorderingInsideOneRepoComparator.getInstance();
    */
      myAdditions = null;
      mySkeletonBuilder = null;
      myNavigation = null;
      myOrder = null;
    //}
    myLines = new BigArray<CommitI>(10);
    myCutCount = -1;
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

    final Integer[] parentsIdx = new Integer[1];
    parentsIdx[0] = 0;

    final Set<Integer> whatToRecount = mySkeletonBuilder == null ? null : new HashSet<Integer>();
    final Map<Integer, Integer> indexRecalculation = new HashMap<Integer, Integer>();
    myStrategy.beforeStart();
    
    // find those ..... long awaited start idx by stupid long iteration since
    // items can NOT be ordered by simple rule
    int idxFrom = findIdx(lines);

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
          whatToRecount.add(i);
          //mySkeletonBuilder.get(commitI.selectRepository(myRootsHolder.getRoots())).consume(commitI, parents.get(parentsIdx[0]), myLines, i);
          //++parentsIdx[0];
        }
      }

      @Override
      protected boolean filter(CommitI commitI) {
        return !commitI.holdsDecoration();
      }

      @Override
      protected String getGroup(CommitI commitI) {
        return mySkeletonBuilder != null ? "" : myStrategy.getGroupName(commitI);
      }

      @Override
      protected CommitI wrapGroup(String s, CommitI item) {
        return new GroupHeaderDatePseudoCommit(s, item.getTime() - 1);
      }

      @Override
      protected void oldBecame(int was, int is) {
        if (mySkeletonBuilder != null && was != is) {
          indexRecalculation.put(was, is);
          /*CommitI commitI = myLines.get(is);
          if (! commitI.holdsDecoration()) {
            mySkeletonBuilder.get(commitI.selectRepository(myRootsHolder.getRoots())).oldBecameNew(was, is);
          }*/
        }
        // todo
        //System.out.println("old: " + was + " became: " + is);
      }
    }.firstPlusSecond(myLines, new ReadonlyList.ArrayListWrapper<CommitI>(lines), myCurrentComparator, mySkeletonBuilder == null ? -1 : idxFrom);
    
    if (mySkeletonBuilder != null) {
      for (SkeletonBuilder skeletonBuilder : mySkeletonBuilder.values()) {
        skeletonBuilder.oldBecameNew(indexRecalculation);
      }

      for (int i = recountFrom; i < myLines.getSize(); i++) {
        final CommitI commitI = myLines.get(i);
        if (mySkeletonBuilder != null && ! commitI.holdsDecoration() && whatToRecount.contains(i)) {
          mySkeletonBuilder.get(commitI.selectRepository(myRootsHolder.getRoots())).consume(commitI, parents.get(parentsIdx[0]), myLines, i);
          ++parentsIdx[0];
        }
      }

      for (Map.Entry<VirtualFile, TreeNavigationImpl> entry : myNavigation.entrySet()) {
        final TreeNavigationImpl navigation = myNavigation.get(entry.getKey());
        navigation.recalcIndex(myLines, mySkeletonBuilder.get(entry.getKey()).getFutureConvertor());
      }
      int size = 0;
      for (VirtualFile file : myOrder) {
        myAdditions.put(file, size);
        size += myNavigation.get(file).getMaximumWires();
      }
    }
  }

  private int findIdx(List<CommitI> lines) {
    final VirtualFile targetRepo = lines.get(0).selectRepository(myRootsHolder.getRoots());
    final long time = lines.get(0).getTime();

    for (int i = myLines.getSize() - 1; i >= 0; i--) {
      final CommitI current = myLines.get(i);
      if (current.selectRepository(myRootsHolder.getRoots()).equals(targetRepo)) {
        return i + 1;      // will be equal to list size sometimes, is that ok?
      } else {
        if (current.getTime() > time) {
          return i + 1;
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

  public void setStrategy(CommitGroupingStrategy strategy) {
    myStrategy = strategy;
  }

  // todo test
  public void printNavigation() {
    for (Map.Entry<VirtualFile, TreeNavigationImpl> entry : myNavigation.entrySet()) {
      if (entry.getKey().getPath().contains("inner")) {
        entry.getValue().printSelf();
      }
    }
  }
}
