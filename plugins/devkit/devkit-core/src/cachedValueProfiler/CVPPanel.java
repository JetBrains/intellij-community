// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.cachedValueProfiler;

import com.intellij.internal.cachedValueProfiler.CVPReader;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TableViewSpeedSearch;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

public class CVPPanel extends JBPanel {

  public CVPPanel(@NotNull VirtualFile file, @NotNull Project project) {
    super(new BorderLayout());

    try {
      List<CVPReader.CVPInfo> infos = CVPReader.deserialize(file.getInputStream());
      TableView<CVPReader.CVPInfo> table = createTable(infos, project);
      add(ScrollPaneFactory.createScrollPane(table), BorderLayout.CENTER);
    }
    catch (IOException e) {
      add(new JBLabel(e.getMessage()));
    }
  }

  @NotNull
  private static TableView<CVPReader.CVPInfo> createTable(@NotNull List<CVPReader.CVPInfo> infos, @NotNull Project project) {
    TableView<CVPReader.CVPInfo> table = new TableView<>(new ListTableModel<>(getColumns(project), infos, 1, SortOrder.DESCENDING));
    table.setDefaultRenderer(Object.class, new ColoredTableCellRenderer() {
      @Override
      protected void customizeCellRenderer(@NotNull JTable table,
                                           @Nullable Object value,
                                           boolean selected,
                                           boolean hasFocus,
                                           int row,
                                           int column) {
        append(String.valueOf(value));
      }
    });
    table.setFillsViewportHeight(true);
    table.getColumnModel().getColumn(0).setPreferredWidth(700);
    registerSpeedSearch(table);
    return table;
  }

  private static ColumnInfo<?, ?>[] getColumns(@NotNull Project project) {
    return new ColumnInfo[]{
      new OriginColumnInfo(project),
      columnInfoL(DevKitBundle.message("cached.value.profiler.column.count"), o -> o.count),
      columnInfoL(DevKitBundle.message("cached.value.profiler.column.total.cost"), o1 -> o1.cost),
      columnInfoD(DevKitBundle.message("cached.value.profiler.column.avg.cost"), o2 -> ((double)o2.cost) / o2.count),
      columnInfoL(DevKitBundle.message("cached.value.profiler.column.total.use.count"), o1 -> o1.used),
      columnInfoD(DevKitBundle.message("cached.value.profiler.column.avg.use.count"), o2 -> ((double)o2.used) / o2.count),
      columnInfoL(DevKitBundle.message("cached.value.profiler.column.total.life.time"), o3 -> o3.lifetime),
      columnInfoD(DevKitBundle.message("cached.value.profiler.column.avg.life.time"), o4 -> ((double)o4.lifetime) / o4.count),
    };
  }

  private static void registerSpeedSearch(TableView<CVPReader.CVPInfo> table) {
    new TableViewSpeedSearch<>(table) {
      @Override
      protected String getItemText(@NotNull CVPReader.CVPInfo element) {
        return element.origin;
      }
    };
  }

  @NotNull
  private static ColumnInfo<CVPReader.CVPInfo, String> columnInfoD(@Nls String name, ToDoubleFunction<CVPReader.CVPInfo> value) {
    return new ColumnInfo<>(name) {
      @Override
      public String valueOf(CVPReader.CVPInfo o) {
        return String.valueOf(Math.round(value(o)));
      }

      private double value(CVPReader.CVPInfo o) {
        return value.applyAsDouble(o);
      }

      @Nullable
      @Override
      public Comparator<CVPReader.CVPInfo> getComparator() {
        return Comparator.comparingDouble(o -> value(o));
      }
    };
  }

  @NotNull
  private static ColumnInfo<CVPReader.CVPInfo, String> columnInfoL(@Nls String name, ToLongFunction<CVPReader.CVPInfo> value) {
    return new ColumnInfo<>(name) {
      @Override
      public String valueOf(CVPReader.CVPInfo o) {
        return String.valueOf(Math.round(value(o)));
      }

      private long value(CVPReader.CVPInfo o) {
        return value.applyAsLong(o);
      }

      @Nullable
      @Override
      public Comparator<CVPReader.CVPInfo> getComparator() {
        return Comparator.comparingLong(o -> value(o));
      }
    };
  }


  private static final class OriginColumnInfo extends ColumnInfo<CVPReader.CVPInfo, String> {
    private final Project myProject;

    private OriginColumnInfo(Project project) {
      super(DevKitBundle.message("cached.value.profiler.column.origin"));
      myProject = project;
    }

    @Override
    public String valueOf(CVPReader.CVPInfo o) {
      return o.origin;
    }

    @Nullable
    @Override
    public Comparator<CVPReader.CVPInfo> getComparator() {
      return Comparator.comparing(o -> o.origin);
    }

    @Override
    public boolean isCellEditable(CVPReader.CVPInfo info) {
      return true;
    }

    @Override
    public TableCellEditor getEditor(CVPReader.CVPInfo info) {
      return new CVPTableCellEditor(myProject);
    }
  }
}
