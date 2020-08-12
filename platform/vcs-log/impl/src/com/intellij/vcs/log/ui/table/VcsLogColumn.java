// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.table;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.text.DateTimeFormatManager;
import com.intellij.util.text.JBDateFormat;
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.graph.DefaultColorGenerator;
import com.intellij.vcs.log.impl.CommonUiProperties;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.paint.GraphCellPainter;
import com.intellij.vcs.log.paint.SimpleGraphCellPainter;
import com.intellij.vcs.log.ui.frame.CommitPresentationUtil;
import com.intellij.vcs.log.ui.render.GraphCommitCell;
import com.intellij.vcs.log.ui.render.GraphCommitCellRenderer;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static com.intellij.util.containers.ContainerUtil.getFirstItem;

/**
 * Do not reorder: this might affect serialization. Add new columns at the end only.
 * If you want to tweak the order of dynamic columns in menus, change {@link #DYNAMIC_COLUMNS} list.
 * If you want to tweak the default order of columns, change the corresponding implementation of {@link com.intellij.vcs.log.impl.VcsLogUiProperties}.
 */
public enum VcsLogColumn {
  ROOT("", FilePath.class) {
    @Override
    public String getLocalizedName() {
      return "";
    }

    @Override
    @NotNull
    public FilePath getValue(@NotNull GraphTableModel model, int row) {
      return model.getVisiblePack().getFilePath(row);
    }

    @Override
    @NotNull
    public FilePath getStubValue(@NotNull GraphTableModel model) {
      return VcsUtil.getFilePath(getFirstItem(model.getLogData().getRoots()));
    }

    @Override
    @NotNull
    public TableCellRenderer createTableCellRenderer(@NotNull VcsLogGraphTable table) {
      return new RootCellRenderer(table.getProperties(), table.getColorManager());
    }

    @Override
    void initColumn(@NotNull VcsLogGraphTable table, @NotNull TableColumn column) {
      column.setResizable(false);
    }
  },
  COMMIT("Subject", GraphCommitCell.class) {
    @Override
    public String getLocalizedName() {
      return VcsLogBundle.message("vcs.log.column.subject");
    }

    @Override
    @NotNull
    public GraphCommitCell getValue(@NotNull GraphTableModel model, int row) {
      VcsCommitMetadata commit = model.getCommitMetadata(row);
      return new GraphCommitCell(commit.getSubject(), model.getRefsAtRow(row),
                                 model.getVisiblePack().getVisibleGraph().getRowInfo(row).getPrintElements());
    }

    @Override
    @NotNull
    public GraphCommitCell getStubValue(@NotNull GraphTableModel model) {
      return new GraphCommitCell("", Collections.emptyList(), Collections.emptyList());
    }

    @Override
    @NotNull
    public TableCellRenderer createTableCellRenderer(@NotNull VcsLogGraphTable table) {
      GraphCellPainter graphCellPainter = new SimpleGraphCellPainter(new DefaultColorGenerator()) {
        @Override
        protected int getRowHeight() {
          return table.getRowHeight();
        }
      };
      return new GraphCommitCellRenderer(table.getLogData(), graphCellPainter, table);
    }
  },
  AUTHOR("Author", String.class) {
    @Override
    public String getLocalizedName() {
      return VcsLogBundle.message("vcs.log.column.author");
    }

    @Override
    @NotNull
    public String getValue(@NotNull GraphTableModel model, int row) {
      return CommitPresentationUtil.getAuthorPresentation(model.getCommitMetadata(row));
    }

    @Override
    @NotNull
    public TableCellRenderer createTableCellRenderer(@NotNull VcsLogGraphTable table) {
      return new VcsLogStringCellRenderer(true);
    }
  },
  DATE("Date", String.class) {
    @Override
    public String getLocalizedName() {
      return VcsLogBundle.message("vcs.log.column.date");
    }

    @Override
    public String getContentSample() {
      if (DateTimeFormatManager.getInstance().isPrettyFormattingAllowed()) return null;
      return JBDateFormat.getFormatter().formatDateTime(DateFormatUtil.getSampleDateTime());
    }

    @Override
    @NotNull
    public String getValue(@NotNull GraphTableModel model, int row) {
      VcsLogUiProperties properties = model.getProperties();
      VcsCommitMetadata commit = model.getCommitMetadata(row);
      boolean preferCommitDate = properties.exists(CommonUiProperties.PREFER_COMMIT_DATE) &&
                                 Boolean.TRUE.equals(properties.get(CommonUiProperties.PREFER_COMMIT_DATE));
      long timeStamp = preferCommitDate ? commit.getCommitTime() : commit.getAuthorTime();
      return timeStamp < 0 ? "" : JBDateFormat.getFormatter().formatPrettyDateTime(timeStamp);
    }

    @Override
    @NotNull
    public TableCellRenderer createTableCellRenderer(@NotNull VcsLogGraphTable table) {
      return new VcsLogStringCellRenderer();
    }
  },
  HASH("Hash", String.class) {
    @Override
    public String getLocalizedName() {
      return VcsLogBundle.message("vcs.log.column.hash");
    }

    @Override
    public String getContentSample() {
      return StringUtil.repeat("e", VcsLogUtil.SHORT_HASH_LENGTH);
    }

    @Override
    @NotNull
    public String getValue(@NotNull GraphTableModel model, int row) {
      return model.getCommitMetadata(row).getId().toShortString();
    }

    @Override
    @NotNull
    public TableCellRenderer createTableCellRenderer(@NotNull VcsLogGraphTable table) {
      return new VcsLogStringCellRenderer();
    }
  };

  @NotNull public static final List<VcsLogColumn> DYNAMIC_COLUMNS = ContainerUtil.immutableList(AUTHOR, DATE, HASH);
  private static final VcsLogColumn @NotNull [] COLUMNS = values(); // to reduce copying overhead

  @NotNull private final String myId;
  @NotNull private final Class<?> myContentClass;

  VcsLogColumn(@NotNull String id, @NotNull Class<?> contentClass) {
    myId = id;
    myContentClass = contentClass;
  }

  public boolean isDynamic() {
    return DYNAMIC_COLUMNS.contains(this);
  }

  @NotNull
  public String getId() {
    return myId;
  }

  abstract public String getLocalizedName();

  @NotNull
  abstract public Object getValue(@NotNull GraphTableModel model, int row);

  @NotNull
  public Object getStubValue(@NotNull GraphTableModel model) {
    return "";
  }

  @NotNull
  abstract public TableCellRenderer createTableCellRenderer(@NotNull VcsLogGraphTable table);

  void initColumn(@NotNull VcsLogGraphTable table, @NotNull TableColumn column) {
  }

  /**
   * @return stable name (to identify column in statistics)
   */
  @NotNull
  public String getStableName() {
    return myId.toLowerCase(Locale.ROOT);
  }

  @NotNull
  Class<?> getContentClass() {
    return myContentClass;
  }

  /**
   * @return content sample to estimate the width of the column,
   * or null if content width may vary significantly and width cannot be estimated from the sample
   */
  @Nullable
  public String getContentSample() {
    return null;
  }

  static boolean isValidColumnOrder(@NotNull List<VcsLogColumn> columnOrder) {
    return columnOrder.contains(ROOT) && columnOrder.contains(COMMIT);
  }

  @NotNull
  public static VcsLogColumn fromOrdinal(int index) {
    return COLUMNS[index];
  }

  public int getModelIndex() {
    return ordinal();
  }

  @NotNull
  public static VcsLogColumn fromModelIndex(int index) {
    return COLUMNS[index];
  }

  public static int count() {
    return COLUMNS.length;
  }
}
