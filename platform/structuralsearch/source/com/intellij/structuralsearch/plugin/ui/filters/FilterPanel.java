// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui.filters;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.*;
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
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class FilterPanel implements FilterTable, ShortFilterTextProvider {

  private final JPanel myFilterPanel;
  final JBListTable myFilterTable;
  final ListTableModel<Filter> myTableModel;

  @NotNull final Project myProject;
  private CompiledPattern myCompiledPattern;
  NamedScriptableDefinition myConstraint;
  LanguageFileType myFileType;

  final Header myHeader = new Header();
  private final ScriptFilter myScriptFilter;
  private final List<FilterAction> myFilters;
  private Runnable myConstraintChangedCallback;

  public FilterPanel(@NotNull Project project, LanguageFileType fileType, Disposable parent) {
    myProject = project;
    myFileType = fileType;
    myFilters = new SmartList<>();
    for (FilterProvider provider : FilterProvider.EP_NAME.getExtensionList()) {
      for (FilterAction filter : provider.getFilters()) {
        myFilters.add(filter);
        filter.setTable(this);
      }
    }
    myScriptFilter = new ScriptFilter(); // initialize last
    myFilters.add(myScriptFilter);
    myScriptFilter.setTable(this);

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
        if (!isValid()) return null;
        return myTableModel.getRowValue(row).getEditor();
      }
    };
    final JBTable table = myFilterTable.getTable();
    table.setTableHeader(new JTableHeader());
    table.setStriped(false);
    table.setBackground(EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground());
    myFilterPanel = ToolbarDecorator.createDecorator(table)
      .disableUpDownActions()
      .setToolbarPosition(ActionToolbarPosition.RIGHT)
      .setAddAction(button -> {
        final RelativePoint point = button.getPreferredPopupPoint();
        if (point == null) return;
        showAddFilterPopup(button.getContextComponent(), point);
      })
      .setAddActionUpdater(e -> isValid() && myFilters.stream().anyMatch(f -> f.isAvailable()))
      .setRemoveAction(button -> {
        myFilterTable.stopEditing();
        final int selectedRow = myFilterTable.getTable().getSelectedRow();
        final Filter filter = myTableModel.getRowValue(selectedRow);
        if (filter instanceof FilterAction) {
          removeFilter((FilterAction)filter);
        }
      })
      .setRemoveActionUpdater(e -> isValid() && myFilterTable.getTable().getSelectedRow() != 0)
      .setPanelBorder(JBUI.Borders.empty())
      .setScrollPaneBorder(JBUI.Borders.empty())
      .createPanel();
    myFilterPanel.setPreferredSize(new Dimension(350, 60));
  }

  @Override
  public void addFilter(FilterAction filter) {
    final JBTable table = myFilterTable.getTable();
    TableUtil.stopEditing(table);
    table.setRowHeight(table.getRowHeight()); // reset
    int index = 0;
    final int max = myTableModel.getRowCount();
    for (; index < max; index++) {
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

  final void initFilter(FilterAction filter, List<? extends PsiElement> nodes, boolean completePattern, boolean target) {
    if (filter.checkApplicable(nodes, completePattern, target)) {
      if (filter.isActive() && !myTableModel.getItems().contains(filter)) {
        if (myTableModel.getRowCount() == 0) {
          myTableModel.addRow(myHeader);
        }
        myTableModel.addRow(filter);
      }
    }
    else if (filter.hasFilter()) {
      filter.clearFilter();
      myConstraintChangedCallback.run();
    }
  }

  @Override
  public final void removeFilter(FilterAction filter) {
    final int index = myTableModel.indexOf(filter);
    if (index >= 0) myTableModel.removeRow(index);
    if (myTableModel.getRowCount() == 1) myTableModel.removeRow(0); // remove header
    filter.clearFilter();
    filter.reset();
    myConstraintChangedCallback.run();
  }

  @Override
  public NamedScriptableDefinition getVariable() {
    return myConstraint;
  }

  @Override
  public String getShortFilterText(NamedScriptableDefinition variable) {
    if (variable == null) {
      return "";
    }
    final StringBuilder builder = new StringBuilder();
    for (FilterAction filter : myFilters) {
      final String text = filter.getShortText(variable);
      if (text.length() > 0) {
        if (builder.length() > 0) builder.append(", ");
        builder.append(text);
      }
    }
    return builder.toString();
  }

  @Override
  @Nullable
  public StructuralSearchProfile getProfile() {
    return StructuralSearchUtil.getProfileByFileType(myFileType);
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
      final ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(SSRBundle.message("add.filter.title"), group, context,
                                                                                  JBPopupFactory.ActionSelectionAid.ALPHA_NUMBERING, false,
                                                                                  ActionPlaces.getPopupPlace("StructuralSearchFilterPanel"));
      popup.show(point);
    }
    else {
      final AnActionEvent event = AnActionEvent.createFromAnAction(myScriptFilter, null, ActionPlaces.UNKNOWN, DataContext.EMPTY_CONTEXT);
      myScriptFilter.actionPerformed(event);
    }
  }

  public JComponent getComponent() {
    return myFilterPanel;
  }

  public void setFileType(@Nullable LanguageFileType fileType) {
    myFileType = fileType;
  }

  public void setCompiledPattern(@Nullable CompiledPattern compiledPattern) {
    myCompiledPattern = compiledPattern;
    showFilters();
  }

  private boolean isValid() {
    return myCompiledPattern != null;
  }

  public void initFilters(@NotNull NamedScriptableDefinition constraint) {
    if (constraint == myConstraint) {
      return;
    }
    myConstraint = constraint;
    resetFilters();
    showFilters();
  }

  public boolean hasVisibleFilter() {
    return myTableModel.getRowCount() > 0;
  }

  private void resetFilters() {
    for (FilterAction filter : myFilters) {
      filter.reset();
    }
  }

  private void showFilters() {
    if (myConstraint == null) {
      return;
    }
    if (!isValid()) {
      myConstraint = null;
      return;
    }
    final String varName = myConstraint.getName();
    final List<PsiElement> nodes = myCompiledPattern.getVariableNodes(varName); // replacement variable has no nodes
    final boolean completePattern = Configuration.CONTEXT_VAR_NAME.equals(varName);
    final boolean target = myConstraint instanceof MatchVariableConstraint &&
                           ((MatchVariableConstraint)myConstraint).isPartOfSearchResults();
    myTableModel.setItems(new SmartList<>());
    ReadAction.run(() -> {
      for (FilterAction filter : myFilters) {
        initFilter(filter, nodes, completePattern, target);
      }
    });

    final String message;
    if (myConstraint instanceof MatchVariableConstraint) {
      message = Configuration.CONTEXT_VAR_NAME.equals(varName)
                ? SSRBundle.message("no.filters.whole.template.label")
                : SSRBundle.message("no.filters.for.0.label", varName);
    }
    else {
      message = SSRBundle.message("no.script.for.0.label", varName);
    }
    final StatusText statusText = myFilterTable.getTable().getEmptyText();
    statusText.setText(message);
    if (isValid()) {
      statusText.appendSecondaryText(myConstraint instanceof MatchVariableConstraint
                                     ? SSRBundle.message("add.filter.label")
                                     : SSRBundle.message("add.script.label"),
                                     SimpleTextAttributes.LINK_ATTRIBUTES,
                                     e -> showAddFilterPopup(myFilterTable.getTable(),
                                                             new RelativePoint(MouseInfo.getPointerInfo().getLocation())));
    }
  }

  public void setConstraintChangedCallback(Runnable callback) {
    myConstraintChangedCallback = callback;
  }

  @Override
  public Runnable getConstraintChangedCallback() {
    return myConstraintChangedCallback;
  }

  public JBTable getTable() {
    return myFilterTable.getTable();
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
                     ? SSRBundle.message("filters.for.whole.template.title")
                     : SSRBundle.message("filters.for.0.title", varName),
                     SimpleTextAttributes.GRAYED_ATTRIBUTES);
      return myLabel;
    }
  }
}
