// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui.filters;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.MatchVariableConstraint;
import com.intellij.structuralsearch.NamedScriptableDefinition;
import com.intellij.structuralsearch.StructuralSearchProfile;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TableUtil;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.table.JBTable;
import com.intellij.ui.table.TableView;
import com.intellij.util.SmartList;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.table.JBListTable;
import com.intellij.util.ui.table.JBTableRowEditor;
import com.intellij.util.ui.table.JBTableRowRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.JTableHeader;
import java.awt.*;
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
  NamedScriptableDefinition myConstraint;
  StructuralSearchProfile myProfile;

  final Header myHeader = new Header();
  private final ScriptFilter myScriptFilter = new ScriptFilter(this);
  private final List<FilterAction> myFilters =
    Arrays.asList(new TextFilter(this), new CountFilter(this), new TypeFilter(this), new ReferenceFilter(this), myScriptFilter);
  private Runnable myConstraintChangedCallback;
  boolean myValid;

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
        return (table, row1, selected, focused) -> myTableModel.getRowValue(row1).getRenderer();
      }

      @Override
      protected JBTableRowEditor getRowEditor(int row) {
        if (!myValid) return null;
        return myTableModel.getRowValue(row).getEditor();
      }
    };
    final JBTable table = myFilterTable.getTable();
    table.setTableHeader(new JTableHeader());
    table.setStriped(false);
    myFilterPanel = ToolbarDecorator.createDecorator(table)
      .disableUpDownActions()
      .setToolbarPosition(ActionToolbarPosition.RIGHT)
      .setAddAction(button -> {
        final RelativePoint point = button.getPreferredPopupPoint();
        if (point == null) return;
        showAddFilterPopup(button.getContextComponent(), point);
      })
      .setAddActionUpdater(e -> myValid && myFilters.stream().anyMatch(f -> f.isAvailable()))
      .setRemoveAction(button -> {
        myFilterTable.stopEditing();
        final int selectedRow = myFilterTable.getTable().getSelectedRow();
        final Filter filter = myTableModel.getRowValue(selectedRow);
        if (filter instanceof FilterAction) {
          removeFilter((FilterAction)filter);
        }
      })
      .setRemoveActionUpdater(e -> myValid && myFilterTable.getTable().getSelectedRow() != 0)
      .setPanelBorder(JBUI.Borders.empty())
      .createPanel();
    myFilterPanel.setPreferredSize(new Dimension(350, 60));
  }

  @Override
  public void addFilter(FilterAction filter) {
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

  public final void initFilter(FilterAction filter, List<? extends PsiElement> nodes, boolean completePattern, boolean target) {
    if (filter.checkApplicable(nodes, completePattern, target)) {
      if (filter.hasFilter() && !myTableModel.getItems().contains(filter)) {
        if (myTableModel.getRowCount() == 0) {
          myTableModel.addRow(myHeader);
        }
        myTableModel.addRow(filter);
      }
    }
    else {
      filter.clearFilter();
    }
  }

  @Override
  public final void removeFilter(FilterAction filter) {
    final int index = myTableModel.indexOf(filter);
    if (index >= 0) myTableModel.removeRow(index);
    if (myTableModel.getRowCount() == 1) myTableModel.removeRow(0); // remove header
    filter.clearFilter();
    myConstraintChangedCallback.run();
  }

  @Override
  public NamedScriptableDefinition getVariable() {
    return myConstraint;
  }

  @Override
  @NotNull
  public StructuralSearchProfile getProfile() {
    return myProfile;
  }

  @Override
  @NotNull
  public Project getProject() {
    return myProject;
  }

  void showAddFilterPopup(Component component, RelativePoint point) {
    myFilterTable.getTable().requestFocus();
    if (myConstraint instanceof MatchVariableConstraint) {
      final DefaultActionGroup group = new DefaultActionGroup(myFilters);
      final DataContext context = DataManager.getInstance().getDataContext(component);
      final ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup("Add Filter", group, context,
                                                                                  JBPopupFactory.ActionSelectionAid.ALPHA_NUMBERING, true,
                                                                                  null);
      popup.show(point);
    }
    else {
      final AnActionEvent event =
        AnActionEvent.createFromAnAction(myScriptFilter, null, ActionPlaces.UNKNOWN,
                                         DataContext.EMPTY_CONTEXT);
      myScriptFilter.actionPerformed(event);
    }
  }

  public JComponent getComponent() {
    return myFilterPanel;
  }

  public void setProfile(@NotNull StructuralSearchProfile profile) {
    myProfile = profile;
  }

  public void setCompiledPattern(@NotNull CompiledPattern compiledPattern) {
    myCompiledPattern = compiledPattern;
  }

  public void setValid(boolean valid) {
    myValid = valid;
    initFilters(myConstraint);
  }

  public boolean isInitialized() {
    return myConstraint != null;
  }

  public void initFilters(@NotNull NamedScriptableDefinition constraint) {
    if (myCompiledPattern == null) {
      return;
    }
    myConstraint = constraint;
    final String varName = myConstraint.getName();
    final List<PsiElement> nodes = myCompiledPattern.getVariableNodes(varName);
    final boolean completePattern = Configuration.CONTEXT_VAR_NAME.equals(varName);
    final boolean target = constraint instanceof MatchVariableConstraint && ((MatchVariableConstraint)constraint).isPartOfSearchResults();
    myTableModel.setItems(new SmartList<>());
    ReadAction.run(() -> {
      for (FilterAction filter : myFilters) {
        initFilter(filter, nodes, completePattern, target);
      }
    });

    final String message;
    if (constraint instanceof MatchVariableConstraint) {
      message = Configuration.CONTEXT_VAR_NAME.equals(varName)
                ? "No filters added for the whole template."
                : "No filters added for $" + varName + "$.";
    }
    else {
      message = "No script added for $" + varName + "$.";
    }
    final StatusText statusText = myFilterTable.getTable().getEmptyText();
    statusText.setText(message);
    if (myValid) {
      statusText.appendSecondaryText(myConstraint instanceof MatchVariableConstraint ? "Add filter" : "Add script",
                                     SimpleTextAttributes.LINK_ATTRIBUTES,
                                     e -> {
                                       final JBTable table = myFilterTable.getTable();
                                       showAddFilterPopup(table, new RelativePoint(table, table.getMousePosition()));
                                     });
    }
  }

  public void setConstraintChangedCallback(Runnable callback) {
    myConstraintChangedCallback = callback;
  }

  @Override
  public Runnable getConstraintChangedCallback() {
    return myConstraintChangedCallback;
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
      myLabel.append(Configuration.CONTEXT_VAR_NAME.equals(varName)
                     ? "Filters for the whole template:"
                     : "Filters for $" + varName + "$:",
                     SimpleTextAttributes.GRAYED_ATTRIBUTES);
      return myLabel;
    }
  }
}
