// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.cachedValueProfiler;

import com.intellij.internal.cachedValueProfiler.CVPInfo;
import com.intellij.internal.cachedValueProfiler.CVPReader;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TableViewSpeedSearch;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;

public class CVPPanel extends JBPanel {

  public CVPPanel(@NotNull VirtualFile file, @NotNull Project project) {
    super(new BorderLayout());

    try {
      List<CVPInfo> infos = CVPReader.deserialize(file.getInputStream());
      TableView<CVPInfo> table = createTable(infos, project);
      add(ScrollPaneFactory.createScrollPane(table), BorderLayout.CENTER);
    }
    catch (IOException e) {
      add(new JBLabel(e.getMessage()));
    }
  }

  @NotNull
  private static TableView<CVPInfo> createTable(@NotNull List<CVPInfo> infos, @NotNull Project project) {
    TableView<CVPInfo> table = new TableView<>(new ListTableModel<>(getColumns(project), infos, 4, SortOrder.ASCENDING));
    table.setFillsViewportHeight(true);
    table.getColumnModel().getColumn(0).setPreferredWidth(1000);
    registerSpeedSearch(table);
    return table;
  }

  private static ColumnInfo[] getColumns(@NotNull Project project) {
    return new ColumnInfo[]{new OriginColumnInfo(project),
      COUNT_COLUMN, TOTAL_USE_COUNT_COLUMN, AVG_USE_COUNT_COLUMN, TOTAL_LIFE_TIME_COLUMN, AVG_LIFETIME_COLUMN};
  }

  private static void registerSpeedSearch(TableView<CVPInfo> table) {
    new TableViewSpeedSearch<>(table) {
      @Override
      protected String getItemText(@NotNull CVPInfo element) {
        return element.getOrigin();
      }
    };
  }

  private static final ColumnInfo<CVPInfo, String> TOTAL_LIFE_TIME_COLUMN = new ColumnInfo<>(
    DevKitBundle.message("cached.value.profiler.column.total.life.time")) {
    @Override
    public String valueOf(CVPInfo o) {
      return String.valueOf(o.getTotalLifeTime());
    }

    @Nullable
    @Override
    public Comparator<CVPInfo> getComparator() {
      return Comparator.comparing(o -> o.getTotalLifeTime());
    }
  };
  private static final ColumnInfo<CVPInfo, String> TOTAL_USE_COUNT_COLUMN = new ColumnInfo<>(
    DevKitBundle.message("cached.value.profiler.column.total.use.count")) {
    @Override
    public String valueOf(CVPInfo o) {
      return String.valueOf(o.getTotalUseCount());
    }

    @Nullable
    @Override
    public Comparator<CVPInfo> getComparator() {
      return Comparator.comparing(o -> o.getTotalUseCount());
    }
  };
  private static final ColumnInfo<CVPInfo, String> COUNT_COLUMN = new ColumnInfo<>(
    DevKitBundle.message("cached.value.profiler.column.count")) {
    @Override
    public String valueOf(CVPInfo o) {
      return String.valueOf(o.getCreatedCount());
    }

    @Nullable
    @Override
    public Comparator<CVPInfo> getComparator() {
      return Comparator.comparing(o -> o.getCreatedCount());
    }
  };
  private static final ColumnInfo<CVPInfo, String> AVG_USE_COUNT_COLUMN = new ColumnInfo<>(
    DevKitBundle.message("cached.value.profiler.column.avg.use.count")) {
    @Override
    public String valueOf(CVPInfo o) {
      return String.valueOf(Math.round(value(o)));
    }

    private double value(CVPInfo o) {
      return ((double)o.getTotalUseCount()) / o.getCreatedCount();
    }

    @Nullable
    @Override
    public Comparator<CVPInfo> getComparator() {
      return Comparator.comparing(o -> value(o));
    }
  };

  private static final ColumnInfo<CVPInfo, String> AVG_LIFETIME_COLUMN = new ColumnInfo<>(
    DevKitBundle.message("cached.value.profiler.column.avg.life.time")) {
    @Override
    public String valueOf(CVPInfo o) {
      return String.valueOf(Math.round(value(o)));
    }

    private double value(CVPInfo o) {
      return ((double)o.getTotalLifeTime()) / o.getCreatedCount();
    }

    @Nullable
    @Override
    public Comparator<CVPInfo> getComparator() {
      return Comparator.comparing(o -> value(o));
    }
  };

  private static final class OriginColumnInfo extends ColumnInfo<CVPInfo, String> {
    private final Project myProject;

    private OriginColumnInfo(Project project) {
      super(DevKitBundle.message("cached.value.profiler.column.origin"));
      myProject = project;
    }

    @Override
    public String valueOf(CVPInfo o) {
      return o.getOrigin();
    }

    @Nullable
    @Override
    public Comparator<CVPInfo> getComparator() {
      return Comparator.comparing(o -> o.getOrigin());
    }

    @Override
    public boolean isCellEditable(CVPInfo info) {
      return true;
    }

    @Nullable
    @Override
    public TableCellEditor getEditor(CVPInfo info) {
      return new CVPTableCellEditor(myProject);
    }
  }
}
