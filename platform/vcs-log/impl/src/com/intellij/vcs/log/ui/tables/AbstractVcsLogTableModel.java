package com.intellij.vcs.log.ui.tables;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.NullVirtualFile;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.data.AroundProvider;
import com.intellij.vcs.log.graph.elements.Node;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.AbstractTableModel;
import java.util.List;

/**
 * @param <CommitColumnClass> commit column class
 * @param <CommitId>          Commit identifier, which can be different depending on the model nature,
 *                            for example, a {@link Hash} or an {@link Integer} or a {@link Node}.
 */
public abstract class AbstractVcsLogTableModel<CommitColumnClass, CommitId> extends AbstractTableModel {

  public static final VirtualFile FAKE_ROOT = NullVirtualFile.INSTANCE;

  public static final int ROOT_COLUMN = 0;
  public static final int COMMIT_COLUMN = 1;
  public static final int AUTHOR_COLUMN = 2;
  public static final int DATE_COLUMN = 3;
  private static final int COLUMN_COUNT = DATE_COLUMN + 1;

  private static final String[] COLUMN_NAMES = {"", "Subject", "Author", "Date"};

  @Override
  public final int getColumnCount() {
    return COLUMN_COUNT;
  }

  @Nullable
  protected abstract VcsShortCommitDetails getShortDetails(int rowIndex);

  @Nullable
  public abstract VcsFullCommitDetails getFullCommitDetails(int row);

  @NotNull
  @Override
  public final Object getValueAt(int rowIndex, int columnIndex) {
    if (rowIndex >= getRowCount() - 1) {
      requestToLoadMore();
    }

    VcsShortCommitDetails data = getShortDetails(rowIndex);
    switch (columnIndex) {
      case ROOT_COLUMN:
        return getRoot(rowIndex);
      case COMMIT_COLUMN:
        return getCommitColumnCell(rowIndex, data);
      case AUTHOR_COLUMN:
        if (data == null) {
          return "";
        }
        else {
          return data.getAuthor().getName();
        }
      case DATE_COLUMN:
        if (data == null || data.getTime() < 0) {
          return "";
        }
        else {
          return DateFormatUtil.formatDateTime(data.getTime());
        }
      default:
        throw new IllegalArgumentException("columnIndex is " + columnIndex + " > " + (COLUMN_COUNT - 1));
    }
  }

  public abstract void requestToLoadMore();

  @Nullable
  public abstract List<Change> getSelectedChanges(int[] selectedRows);

  @NotNull
  protected abstract VirtualFile getRoot(int rowIndex);

  @NotNull
  protected abstract CommitColumnClass getCommitColumnCell(int index, @Nullable VcsShortCommitDetails details);

  @NotNull
  protected abstract Class<CommitColumnClass> getCommitColumnClass();

  @NotNull
  public abstract AroundProvider<CommitId> getAroundProvider();

  /**
   * Returns the Hash of the commit displayed in the given row.
   * May be null if there is no commit in the row
   * (such situations may appear, for example, if graph is filtered by branch, as described in IDEA-115442).
   */
  @Nullable
  public abstract Hash getHashAtRow(int row);

  @Override
  public Class<?> getColumnClass(int column) {
    switch (column) {
      case ROOT_COLUMN:
        return VirtualFile.class;
      case COMMIT_COLUMN:
        return getCommitColumnClass();
      case AUTHOR_COLUMN:
        return String.class;
      case DATE_COLUMN:
        return String.class;
      default:
        throw new IllegalArgumentException("columnIndex is " + column + " > " + (COLUMN_COUNT - 1));
    }
  }

  @Override
  public String getColumnName(int column) {
    return COLUMN_NAMES[column];
  }

}
