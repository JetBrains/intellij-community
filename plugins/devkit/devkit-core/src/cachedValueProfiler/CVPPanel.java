// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

import javax.swing.*;
import javax.swing.table.JTableHeader;
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
    increaseHeaderFontSize(table, 1.2);
    return table;
  }

  private static ColumnInfo[] getColumns(@NotNull Project project) {
    return new ColumnInfo[]{new OriginColumnInfo(project), TOTAL_LIFE_TIME_COLUMN, TOTAL_USE_COUNT_COLUMN, CREATED_COUNT_COLUMN, RATIO_COLUMN};
  }

  private static void increaseHeaderFontSize(TableView<CVPInfo> table, double ratio) {
    JTableHeader header = table.getTableHeader();
    Font font = header.getFont();
    Font derivedFont = font.deriveFont((float)(font.getSize() * ratio));
    header.setFont(derivedFont);
  }

  private static void registerSpeedSearch(TableView<CVPInfo> table) {
    new TableViewSpeedSearch<CVPInfo>(table) {
      @Override
      protected String getItemText(@NotNull CVPInfo element) {
        return element.getOrigin();
      }
    };
  }

  private static final ColumnInfo<CVPInfo, String> TOTAL_LIFE_TIME_COLUMN = new ColumnInfo<CVPInfo, String>("Total Life Time") {
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
  private static final ColumnInfo<CVPInfo, String> TOTAL_USE_COUNT_COLUMN = new ColumnInfo<CVPInfo, String>("Total Use Count") {
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
  private static final ColumnInfo<CVPInfo, String> CREATED_COUNT_COLUMN = new ColumnInfo<CVPInfo, String>("Created") {
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
  private static final ColumnInfo<CVPInfo, String> RATIO_COLUMN = new ColumnInfo<CVPInfo, String>("Total Use Count / Created") {
    @Override
    public String valueOf(CVPInfo o) {
      return String.valueOf(value(o));
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

  private static class OriginColumnInfo extends ColumnInfo<CVPInfo, String> {
    private final Project myProject;

    private OriginColumnInfo(Project project) {super("Origin");
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
