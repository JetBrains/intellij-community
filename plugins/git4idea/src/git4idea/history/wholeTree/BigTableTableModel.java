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

import com.intellij.openapi.util.Comparing;
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
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * @author irengrig
 */
public class BigTableTableModel extends AbstractTableModel {
  public final static Object LOADING = new Object();
  @Nullable
  private TreeNavigation myNavigation;
  @NotNull
  private final List<ColumnInfo> myColumns;
  private RootsHolder myRootsHolder;
  @Nullable
  private StepList<CommitI> myLines;
  private int myCutCount;
  private DetailsCache myCache;
  private Runnable myInit;
  private final DateChangeListGroupingStrategy myStrategy;

  public BigTableTableModel(@NotNull final List<ColumnInfo> columns, Runnable init) {
    myColumns = columns;
    myInit = init;
    myStrategy = new DateChangeListGroupingStrategy();
    myLines = new BigArray<CommitI>(10);
    myCutCount = -1;
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

  public void clear() {
    myNavigation = null;
    myLines = new BigArray<CommitI>(10);
    myCutCount = -1;
  }

  public void cutAt(final int lastShownItemIdx) {
    myCutCount = lastShownItemIdx + 1;
  }

  public void restore() {
    myCutCount = -1;
  }

  public void appendData(final List<CommitI> lines, final List<List<AbstractHash>> treeNavigation) {
    myStrategy.beforeStart();
    new GroupingMerger<CommitI, String>() {
      @Override
      protected boolean filter(CommitI commitI) {
        return ! commitI.holdsDecoration();
      }
      @Override
      protected String getGroup(CommitI commitI) {
        return myStrategy.getGroupName(new Date(commitI.getTime()));
      }

      @Override
      protected CommitI wrapGroup(String s, CommitI item) {
        return new GroupHeaderDatePseudoCommit(s, item.getTime() - 1);
      }
    }.firstPlusSecond(myLines, new ReadonlyList.ArrayListWrapper<CommitI>(lines), CommitIComparator.getInstance());
  }

  private static class CommitIComparator implements Comparator<CommitI> {
    private final static CommitIComparator ourInstance = new CommitIComparator();

    public static CommitIComparator getInstance() {
      return ourInstance;
    }

    @Override
    public int compare(CommitI o1, CommitI o2) {
      long result = o1.getTime() - o2.getTime();
      if (result == 0) {
        if (Comparing.equal(o1.getHash(), o2.getHash())) return 0;

        final Integer rep1 = o1.selectRepository(SelectorList.getInstance());
        final Integer rep2 = o2.selectRepository(SelectorList.getInstance());
        result = rep1 - rep2;

        if (result == 0) {
          return -1;  // actually, they are still not equal -> keep order
        }
      }
      // descending
      return result == 0 ? 0 : (result < 0 ? 1 : -1);
    }
  }

  public void setCache(DetailsCache cache) {
    myCache = cache;
  }

  public void setRootsHolder(RootsHolder rootsHolder) {
    myRootsHolder = rootsHolder;
  }
}
