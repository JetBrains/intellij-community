// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.ui;

import com.intellij.codeInspection.ui.InspectionOptionsPanel;
import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.actionSystem.ActionToolbarPosition;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UI;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.util.Collection;

public final class UiUtils {

  private UiUtils() {
  }

  public static void setComponentSize(Component component, int rows, int columns) {
    final FontMetrics fontMetrics = component.getFontMetrics(component.getFont());
    final int width = fontMetrics.charWidth('m') * columns;
    component.setPreferredSize(new Dimension(width, fontMetrics.getHeight() * rows));
  }

  public static JPanel createAddRemovePanel(final ListTable table) {
    final JPanel panel = ToolbarDecorator.createDecorator(table)
      .setToolbarPosition(ActionToolbarPosition.LEFT)
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          final ListWrappingTableModel tableModel = table.getModel();
          tableModel.addRow();
          EventQueue.invokeLater(() -> {
            final int lastRowIndex = tableModel.getRowCount() - 1;
            editTableCell(table, lastRowIndex, 0);
          });
        }
      })
      .setRemoveAction(button -> TableUtil.removeSelectedItems(table))
      .disableUpDownActions().createPanel();
    panel.setMinimumSize(InspectionOptionsPanel.getMinimumListSize());
    return panel;
  }

  public static JPanel createAddRemovePanel(final ListTable table, @NlsContexts.Label final String panelLabel, boolean removeHeader) {
    if (removeHeader) table.setTableHeader(null);
    final JPanel panel = createAddRemovePanel(table);
    return UI.PanelFactory.panel(panel).withLabel(panelLabel).moveLabelOnTop().resizeY(true).createPanel();
  }

  public static JPanel createAddRemoveTreeClassChooserPanel(final ListTable table, @NlsContexts.DialogTitle final String chooserTitle,
                                                            @NonNls String... ancestorClasses) {
    final ClassFilter filter;
    if (ancestorClasses.length == 0) {
      filter = c -> !PsiUtil.isLocalClass(c);
    }
    else {
      filter = new SubclassFilter(ancestorClasses);
    }
    final JPanel panel = ToolbarDecorator.createDecorator(table)
      .disableUpDownActions()
      .setToolbarPosition(ActionToolbarPosition.LEFT)
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          final DataContext dataContext = DataManager.getInstance().getDataContext(table);
          final Project project = CommonDataKeys.PROJECT.getData(dataContext);
          final int rowIndex;
          final ListWrappingTableModel tableModel = table.getModel();
          if (project == null) {
            tableModel.addRow();
            rowIndex = tableModel.getRowCount() - 1;
          }
          else {
            final TreeClassChooserFactory chooserFactory = TreeClassChooserFactory.getInstance(project);
            final TreeClassChooser classChooser =
              chooserFactory.createWithInnerClassesScopeChooser(chooserTitle, GlobalSearchScope.allScope(project), filter, null);
            classChooser.showDialog();
            final PsiClass selectedClass = classChooser.getSelected();
            if (selectedClass == null) {
              return;
            }
            final String qualifiedName = selectedClass.getQualifiedName();
            final int index = tableModel.indexOf(qualifiedName, 0);
            if (index < 0) {
              tableModel.addRow(qualifiedName);
              rowIndex = tableModel.getRowCount() - 1;
            }
            else {
              rowIndex = index;
            }
          }
          editTableCell(table, rowIndex, table.getColumnCount() > 1 && project != null ? 1 : 0);
        }
      })
      .setRemoveAction(button -> TableUtil.removeSelectedItems(table))
      .createPanel();
    panel.setMinimumSize(InspectionOptionsPanel.getMinimumListSize());
    panel.setPreferredSize(InspectionOptionsPanel.getMinimumListSize());
    return panel;
  }

  public static JPanel createAddRemoveTreeClassChooserPanel(@NlsContexts.DialogTitle final String chooserTitle,
                                                            @NlsContexts.Label final String treeLabel,
                                                            final ListTable table,
                                                            boolean removeHeader,
                                                            @NonNls String... ancestorClasses) {
    if (removeHeader) table.setTableHeader(null);
    final JPanel panel = createAddRemoveTreeClassChooserPanel(table, chooserTitle, ancestorClasses);
    return UI.PanelFactory.panel(panel).withLabel(treeLabel).moveLabelOnTop().resizeY(true).createPanel();
  }

  private static void editTableCell(final ListTable table, final int row, final int column) {
    final ListSelectionModel selectionModel = table.getSelectionModel();
    selectionModel.setSelectionInterval(row, row);
    EventQueue.invokeLater(() -> {
      final ListWrappingTableModel tableModel = table.getModel();
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(table, true));
      final Rectangle rectangle = table.getCellRect(row, column, true);
      table.scrollRectToVisible(rectangle);
      table.editCellAt(row, column);
      final TableCellEditor editor = table.getCellEditor();
      final Component component = editor.getTableCellEditorComponent(table, tableModel.getValueAt(row, column), true, row, column);
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(component, true));
    });
  }

  public static JPanel createTreeClassChooserList(final Collection<String> collection,
                                                  @NlsContexts.Label String borderTitle,
                                                  final @NlsContexts.DialogTitle String chooserTitle,
                                                  String... ancestorClasses) {
    final ClassFilter filter;
    if (ancestorClasses.length == 0) {
      filter = ClassFilter.ALL;
    }
    else {
      filter = new SubclassFilter(ancestorClasses);
    }
    final JBList<String> list = new JBList<>(collection);
    list.setBorder(JBUI.Borders.empty());

    final JPanel panel = ToolbarDecorator.createDecorator(list)
      .setToolbarPosition(ActionToolbarPosition.LEFT)
      .disableUpDownActions()
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton anActionButton) {
          final DataContext dataContext = DataManager.getInstance().getDataContext(list);
          final Project project = CommonDataKeys.PROJECT.getData(dataContext);
          if (project == null) {
            return;
          }
          final TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project)
            .createNoInnerClassesScopeChooser(chooserTitle, GlobalSearchScope.allScope(project), filter, null);
          chooser.showDialog();
          final PsiClass selected = chooser.getSelected();
          if (selected == null) {
            return;
          }
          final String qualifiedName = selected.getQualifiedName();
          final DefaultListModel<String> model = (DefaultListModel<String>)list.getModel();
          final int index = model.indexOf(qualifiedName);
          if (index < 0) {
            model.addElement(qualifiedName);
            collection.add(qualifiedName);
          }
          else {
            list.setSelectedIndex(index);
          }
        }
      })
      .setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton anActionButton) {
          collection.remove(list.getSelectedValue());
          ListUtil.removeSelectedItems(list);
        }
      }).createPanel();
    panel.setMinimumSize(InspectionOptionsPanel.getMinimumListSize());
    panel.setPreferredSize(InspectionOptionsPanel.getMinimumListSize());
    return UI.PanelFactory.panel(panel).withLabel(borderTitle).moveLabelOnTop().resizeY(true).createPanel();
  }

  private static final class SubclassFilter implements ClassFilter {

    private final String[] ancestorClasses;

    private SubclassFilter(String[] ancestorClasses) {
      this.ancestorClasses = ancestorClasses;
    }

    @Override
    public boolean isAccepted(PsiClass aClass) {
      if (PsiUtil.isLocalClass(aClass)) return false;
      for (String ancestorClass : ancestorClasses) {
        if (InheritanceUtil.isInheritor(aClass, ancestorClass)) {
          return true;
        }
      }
      return false;
    }
  }
}
