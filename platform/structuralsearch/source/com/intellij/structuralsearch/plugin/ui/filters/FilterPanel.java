// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui.filters;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionToolbarPosition;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.MatchVariableConstraint;
import com.intellij.structuralsearch.StructuralSearchProfile;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.table.JBTable;
import com.intellij.ui.table.TableView;
import com.intellij.util.SmartList;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.table.JBListTable;
import com.intellij.util.ui.table.JBTableRowEditor;
import com.intellij.util.ui.table.JBTableRowRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class FilterPanel implements FilterTable {

  private final JPanel myFilterPanel;
  final JBListTable myFilterTable;
  final ListTableModel<Filter> myTableModel;

  @NotNull final Project myProject;
  private CompiledPattern myCompiledPattern;
  MatchVariableConstraint myConstraint;
  StructuralSearchProfile myProfile;

  final Header myHeader = new Header();
  private final List<FilterAction> myFilters =
    Arrays.asList(new TextFilter(this), new CountFilter(this), new TypeFilter(this), new ReferenceFilter(this), new ScriptFilter(this));

  public FilterPanel(@NotNull Project project, StructuralSearchProfile profile, Disposable parent) {
    myProject = project;
    myProfile = profile;
    myTableModel = new ListTableModel<>(new ColumnInfo[]{new ColumnInfo<Filter, Filter>("") {
      @Nullable
      @Override
      public Filter valueOf(Filter s) {
        return s;
      }
    }}, new SmartList<>());
    myFilterTable = new JBListTable(new TableView<>(myTableModel), parent) {

      @Override
      protected JBTableRowRenderer getRowRenderer(int row) {
        return new JBTableRowRenderer() {
          @Override
          public JComponent getRowRendererComponent(JTable table, int row, boolean selected, boolean focused) {
            return myTableModel.getRowValue(row).getRenderer();
          }
        };
      }

      @Override
      protected JBTableRowEditor getRowEditor(int row) {
        return myTableModel.getRowValue(row).getEditor();
      }
    };
    final JBTable table = myFilterTable.getTable();
    table.setTableHeader(new JTableHeader());
    table.setStriped(false);
    myFilterPanel = ToolbarDecorator.createDecorator(table)
                                    .disableUpDownActions()
                                    .setToolbarPosition(ActionToolbarPosition.RIGHT)
                                    .setAddAction(new AnActionButtonRunnable() {
                                      @Override
                                      public void run(AnActionButton button) {
                                        final RelativePoint point = button.getPreferredPopupPoint();
                                        if (point == null) return;
                                        showAddFilterPopup(button.getContextComponent(), point);
                                      }
                                    })
                                    .setRemoveAction(new AnActionButtonRunnable() {
                                      @Override
                                      public void run(AnActionButton button) {
                                        myFilterTable.stopEditing();
                                        final int selectedRow = myFilterTable.getTable().getSelectedRow();
                                        final Filter filter = myTableModel.getRowValue(selectedRow);
                                        if (filter instanceof FilterAction) {
                                          removeFilter((FilterAction)filter);
                                        }
                                      }
                                    })
                                    .setRemoveActionUpdater(new AnActionButtonUpdater() {
                                      @Override
                                      public boolean isEnabled(AnActionEvent e) {
                                        return myFilterTable.getTable().getSelectedRow() != 0;
                                      }
                                    })
                                    .setPanelBorder(null)
                                    .createPanel();
    myFilterPanel.setPreferredSize(new Dimension(350, 60));
    myFilterPanel.setBorder(BorderFactory.createCompoundBorder(JBUI.Borders.empty(3, 0), myFilterPanel.getBorder()));
  }

  public void addFilter(FilterAction filter) {
    filter.getTemplatePresentation().setEnabledAndVisible(false);
    final JBTable table = myFilterTable.getTable();
    TableUtil.stopEditing(table);
    table.setRowHeight(table.getRowHeight()); // reset
    int index = 0;
    for (int max = myTableModel.getRowCount(); index < max; index++) {
      if (filter.position() < myTableModel.getItem(index).position()) {
        break;
      }
    }
    if (index == 0) {
      myTableModel.addRow(myHeader);
      index = 1;
    }
    myTableModel.insertRow(index, filter);
    table.editCellAt(index, 0);
    table.setRowSelectionInterval(index, index);
    table.setColumnSelectionInterval(0, 0);
    TableUtil.updateScroller(table);
    final Component editorComponent = table.getEditorComponent();
    if (editorComponent != null) {
      table.scrollRectToVisible(editorComponent.getBounds());
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(editorComponent, true));
    }
  }

  public final void addFilterIfPresent(FilterAction filter) {
    if (filter.hasFilter()) {
      filter.getTemplatePresentation().setEnabledAndVisible(false);
      if (myTableModel.getRowCount() == 0) {
        myTableModel.addRow(myHeader);
      }
      myTableModel.addRow(filter);
    }
    else {
      filter.getTemplatePresentation().setEnabledAndVisible(true);
    }
  }

  public final void initFilter(FilterAction filter, List<PsiElement> nodes, boolean completePattern, boolean target) {
    if (filter.isApplicable(nodes, completePattern, target) && !myTableModel.getItems().contains(filter)) {
      addFilterIfPresent(filter);
    }
    else {
      filter.clearFilter();
      filter.getTemplatePresentation().setEnabledAndVisible(false);
    }
  }

  public final void removeFilter(FilterAction filter) {
    final int index = myTableModel.indexOf(filter);
    if (index >= 0) myTableModel.removeRow(index);
    if (myTableModel.getRowCount() == 1) myTableModel.removeRow(0); // remove header
    filter.getTemplatePresentation().setEnabledAndVisible(true);
    filter.clearFilter();
  }

  @Override
  public MatchVariableConstraint getConstraint() {
    return myConstraint;
  }

  @NotNull
  public StructuralSearchProfile getProfile() {
    return myProfile;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  void showAddFilterPopup(Component component, RelativePoint point) {
    myFilterTable.getTable().requestFocus();
    final DefaultActionGroup group = new DefaultActionGroup(myFilters);
    final DataContext context = DataManager.getInstance().getDataContext(component);
    final ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup("Add Filter", group, context,
                                                                                JBPopupFactory.ActionSelectionAid.ALPHA_NUMBERING, true, null);
    popup.show(point);
  }

  public JComponent getComponent() {
    return myFilterPanel;
  }

  public void setProfile(StructuralSearchProfile profile) {
    myProfile = profile;
  }

  public void setCompiledPattern(@NotNull CompiledPattern compiledPattern) {
    myCompiledPattern = compiledPattern;
  }

  public boolean isInitialized() {
    return myConstraint != null;
  }

  public void initFilters(@NotNull MatchVariableConstraint constraint) {
    myConstraint = constraint;
    final String varName = myConstraint.getName();
    final List<PsiElement> nodes = myCompiledPattern.getVariableNodes(varName);
    final boolean completePattern = Configuration.CONTEXT_VAR_NAME.equals(varName);
    final boolean target = myConstraint.isPartOfSearchResults();
    myTableModel.setItems(new SmartList<>());
    myFilters.forEach(f -> initFilter(f, nodes, completePattern, target));

    final String message = Configuration.CONTEXT_VAR_NAME.equals(varName)
                           ? "No filters added for the complete match."
                           : "No Filters added for $" + varName + "$.";
    myFilterTable.getTable().getEmptyText().setText(message)
                 .appendSecondaryText("Add filter", SimpleTextAttributes.LINK_ATTRIBUTES,
                                      new ActionListener() {
                                        @Override
                                        public void actionPerformed(ActionEvent e) {
                                          final JBTable table = myFilterTable.getTable();
                                          showAddFilterPopup(table, new RelativePoint(table, table.getMousePosition()));
                                        }
                                      });
  }

  private class Header implements Filter {

    private final SimpleColoredComponent myLabel = new SimpleColoredComponent();

    Header() {}

    @Override
    public int position() {
      return 0;
    }

    @Override
    public SimpleColoredComponent getRenderer() {
      myLabel.clear();
      final String varName = myConstraint.getName();
      myLabel.append(Configuration.CONTEXT_VAR_NAME.equals(varName) ? "Filters for the Complete Match:" : "Filters for $" + varName + "$:",
                     SimpleTextAttributes.GRAYED_ATTRIBUTES);
      return myLabel;
    }
  }
}
