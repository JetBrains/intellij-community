// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.MatchVariableConstraint;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.StructuralSearchProfile;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.fields.IntegerField;
import com.intellij.ui.table.JBTable;
import com.intellij.ui.table.TableView;
import com.intellij.util.SmartList;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.table.JBListTable;
import com.intellij.util.ui.table.JBTableRow;
import com.intellij.util.ui.table.JBTableRowEditor;
import com.intellij.util.ui.table.JBTableRowRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class FilterPanel {

  private final JPanel myFilterPanel;
  final JBListTable myFilterTable;
  final ListTableModel<Filter> myTableModel;

  @NotNull private final Project myProject;
  private CompiledPattern myCompiledPattern;
  MatchVariableConstraint myConstraint;
  StructuralSearchProfile myProfile;

  final Header myHeader = new Header();
  private final TextFilter myTextFilter = new TextFilter();
  private final CountFilter myCountFilter = new CountFilter();
  private final TypeFilter myTypeFilter = new TypeFilter();
  private final ReferenceFilter myReferenceFilter = new ReferenceFilter();

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
                                          ((FilterAction)filter).removeFilter();
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

  void showAddFilterPopup(Component component, RelativePoint point) {
    myFilterTable.getTable().requestFocus();
    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(myTextFilter);
    group.add(myCountFilter);
    group.add(myTypeFilter);
    group.add(myReferenceFilter);

    group.add(new AnAction("Script") {
      @Override
      public void actionPerformed(AnActionEvent e) {
        assert false : "not implemented";
      }
    });
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

  public void setCompiledPattern(CompiledPattern compiledPattern) {
    myCompiledPattern = compiledPattern;
  }

  public void setFilters(MatchVariableConstraint constraint) {
    myConstraint = constraint;
    final String varName = myConstraint.getName();
    final List<PsiElement> nodes = myCompiledPattern.getVariableNodes(varName);
    final boolean completePattern = Configuration.CONTEXT_VAR_NAME.equals(varName);
    final boolean target = myConstraint.isPartOfSearchResults();
    myTableModel.setItems(new SmartList<>());
    myTextFilter.init(nodes, completePattern, target);
    myCountFilter.init(nodes, completePattern, target);
    myTypeFilter.init(nodes, completePattern, target);

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

  EditorTextField createTextComponent(String text) {
    return createEditorComponent(text, "1.txt");
  }

  EditorTextField createRegexComponent(String text) {
    return createEditorComponent(text, "1.regexp");
  }

  private EditorTextField createScriptComponent(String text) {
    return createEditorComponent(text, "1.groovy");
  }

  @NotNull
  private EditorTextField createEditorComponent(String text, String fileName) {
    return new EditorTextField(text, myProject, getFileType(fileName));
  }

  private static FileType getFileType(final String fileName) {
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
    if (fileType == FileTypes.UNKNOWN) fileType = FileTypes.PLAIN_TEXT;
    return fileType;
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

  private class TextFilter extends FilterAction {

    boolean showHierarchy;

    TextFilter() {
      super("Text");
    }

    @Override
    public int position() {
      return 1;
    }

    @Override
    protected boolean hasFilter() {
      return !StringUtil.isEmpty(myConstraint.getRegExp());
    }

    public void clearFilter() {
      myConstraint.setRegExp("");
      myConstraint.setWholeWordsOnly(false);
      myConstraint.setWithinHierarchy(false);
    }

    @Override
    public boolean isApplicable(List<PsiElement> nodes, boolean completePattern, boolean target) {
      showHierarchy = myProfile.isApplicableConstraint(UIUtil.TEXT_HIERARCHY, nodes, completePattern, target);
      return myProfile.isApplicableConstraint(UIUtil.TEXT, nodes, completePattern, target);
    }

    @Override
    protected void setLabel(SimpleColoredComponent component) {
      myLabel.append("text=");
      if (myConstraint.isInvertRegExp()) myLabel.append("!");
      myLabel.append(myConstraint.getRegExp());
      if (myConstraint.isWholeWordsOnly()) myLabel.append(", whole words", SimpleTextAttributes.GRAYED_ATTRIBUTES);
      if (myConstraint.isWithinHierarchy()) myLabel.append(", within hierarchy", SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }

    @Override
    public FilterEditor getEditor() {
      return new FilterEditor(myConstraint) {

        private final EditorTextField myTextField = createRegexComponent("");
        private final JCheckBox myWordsCheckBox = new JCheckBox("Words", false);
        private final JCheckBox myHierarchyCheckBox = new JCheckBox("Within type hierarchy", false);
        private final JLabel myNameLabel = new JLabel("name=");
        private final ContextHelpLabel myHelpLabel = ContextHelpLabel.create("Text of the match is checked against the provided pattern.");

        protected void layoutComponents() {
          final GroupLayout layout = new GroupLayout(this);
          setLayout(layout);
          layout.setAutoCreateContainerGaps(true);

          layout.setHorizontalGroup(
            layout.createParallelGroup()
                  .addGroup(
                    layout.createSequentialGroup()
                          .addComponent(myNameLabel)
                          .addComponent(myTextField)
                          .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, 1, 1)
                          .addComponent(myHelpLabel)
                  )
                  .addGroup(
                    layout.createSequentialGroup()
                          .addComponent(myWordsCheckBox)
                          .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
                          .addComponent(myHierarchyCheckBox)
                  )
          );
          layout.setVerticalGroup(
            layout.createSequentialGroup()
                  .addGroup(
                    layout.createParallelGroup(GroupLayout.Alignment.CENTER)
                          .addComponent(myNameLabel)
                          .addComponent(myTextField)
                          .addComponent(myHelpLabel)
                  )
                  .addGroup(
                    layout.createParallelGroup(GroupLayout.Alignment.CENTER)
                          .addComponent(myWordsCheckBox)
                          .addComponent(myHierarchyCheckBox)
                  )
          );
        }

        @Override
        protected void loadValues() {
          myTextField.setText(myConstraint.getRegExp());
          myWordsCheckBox.setSelected(myConstraint.isWholeWordsOnly());
          myHierarchyCheckBox.setSelected(myConstraint.isWithinHierarchy());
          myHierarchyCheckBox.setVisible(showHierarchy);
        }

        @Override
        public void saveValues() {
          myConstraint.setRegExp(myTextField.getText());
          myConstraint.setWholeWordsOnly(myWordsCheckBox.isSelected());
          myConstraint.setWithinHierarchy(myHierarchyCheckBox.isSelected());
        }

        @Override
        public JComponent getPreferredFocusedComponent() {
          return myTextField;
        }

        @Override
        public JComponent[] getFocusableComponents() {
          return new JComponent[] {myTextField};
        }
      };
    }
  }

  private class CountFilter extends FilterAction {

    boolean myMinZero;
    boolean myMaxUnlimited;

    CountFilter() {
      super("Count");
    }

    @Override
    public int position() {
      return 2;
    }

    @Override
    protected boolean hasFilter() {
      return myConstraint.getMinCount() != 1 || myConstraint.getMaxCount() != 1;
    }

    @Override
    protected void clearFilter() {
      myConstraint.setMinCount(1);
      myConstraint.setMaxCount(1);
    }

    @Override
    public boolean isApplicable(List<PsiElement> nodes, boolean completePattern, boolean target) {
      myMinZero = myProfile.isApplicableConstraint(UIUtil.MINIMUM_ZERO, nodes, completePattern, false);
      myMaxUnlimited = myProfile.isApplicableConstraint(UIUtil.MAXIMUM_UNLIMITED, nodes, completePattern, false);
      return myMinZero || myMaxUnlimited;
    }

    @Override
    protected void setLabel(SimpleColoredComponent component) {
      final int min = myConstraint.getMinCount();
      final int max = myConstraint.getMaxCount();
      myLabel.append("count=[" + min + "," + (max == Integer.MAX_VALUE ? "∞" : max) + ']');
    }

    @Override
    public FilterEditor getEditor() {
      return new FilterEditor(myConstraint) {

        private final IntegerField myMinField = new IntegerField();
        private final IntegerField myMaxField = new IntegerField();
        private final JLabel myMinLabel = new JLabel("min=");
        private final JLabel myMaxLabel = new JLabel("max=");

        {
          myMinField.getValueEditor().addListener(newValue -> {
            if (myMinField.getValueEditor().isValid(newValue) && myMaxField.getValue() < newValue) myMaxField.setValue(newValue);
          });
          myMaxField.getValueEditor().addListener(newValue -> {
            if (myMaxField.getValueEditor().isValid(newValue) && myMinField.getValue() > newValue) myMinField.setValue(newValue);
          });
        }

        protected void layoutComponents() {
          final GroupLayout layout = new GroupLayout(this);
          setLayout(layout);
          layout.setAutoCreateContainerGaps(true);

          layout.setHorizontalGroup(
            layout.createSequentialGroup()
                  .addComponent(myMinLabel)
                  .addComponent(myMinField)
                  .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED, 20, 20)
                  .addComponent(myMaxLabel)
                  .addComponent(myMaxField)
          );
          layout.setVerticalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                  .addComponent(myMinLabel)
                  .addComponent(myMinField)
                  .addComponent(myMaxLabel)
                  .addComponent(myMaxField)
          );
        }

        @Override
        protected void loadValues() {
          myMinField.setMinValue(myMinZero ? 0 : 1);
          myMinField.setDefaultValue(myMinZero ? 0 : 1);
          myMinField.setDefaultValueText(myMinZero ? "0" : "1");
          myMinField.setValue(myConstraint.getMinCount());
          myMinField.selectAll();

          myMaxField.setMaxValue(myMaxUnlimited ? Integer.MAX_VALUE : 1);
          myMaxField.setDefaultValue(myMaxUnlimited ? Integer.MAX_VALUE : 1);
          myMaxField.setDefaultValueText(myMaxUnlimited ? SSRBundle.message("editvarcontraints.unlimited") : "1");
          myMaxField.setValue(myConstraint.getMaxCount());
          myMaxField.selectAll();
        }

        @Override
        public void saveValues() {
          myConstraint.setMinCount(myMinField.getValue());
          myConstraint.setMaxCount(myMaxField.getValue());
        }

        @Override
        public JComponent getPreferredFocusedComponent() {
          return myMinField;
        }

        @Override
        public JComponent[] getFocusableComponents() {
          return new JComponent[] {myMinField, myMaxField};
        }
      };
    }
  }

  private class TypeFilter extends FilterAction {

    TypeFilter() {
      super("Type");
    }

    @Override
    public int position() {
      return 3;
    }

    @Override
    protected boolean hasFilter() {
      return !StringUtil.isEmpty(myConstraint.getNameOfExprType());
    }

    @Override
    protected void clearFilter() {
      myConstraint.setNameOfExprType("");
      myConstraint.setInvertExprType(false);
      myConstraint.setExprTypeWithinHierarchy(false);
    }

    @Override
    public boolean isApplicable(List<PsiElement> nodes, boolean completePattern, boolean target) {
      return myProfile.isApplicableConstraint(UIUtil.TYPE, nodes, completePattern, target);
    }

    @Override
    protected void setLabel(SimpleColoredComponent component) {
      myLabel.append("type=");
      if (myConstraint.isInvertExprType()) myLabel.append("!");
      myLabel.append(myConstraint.getNameOfExprType());
      if (myConstraint.isExprTypeWithinHierarchy()) myLabel.append(", within hierarchy", SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }

    @Override
    public FilterEditor getEditor() {
      return new FilterEditor(myConstraint) {

        private final EditorTextField myTextField = createTextComponent("");
        private final JLabel myTypeLabel = new JLabel("type=");
        private final JCheckBox myHierarchyCheckBox = new JCheckBox("Within type hierarchy", false);

        protected void layoutComponents() {
          final GroupLayout layout = new GroupLayout(this);
          setLayout(layout);
          layout.setAutoCreateContainerGaps(true);

          layout.setHorizontalGroup(
            layout.createParallelGroup()
                  .addGroup(
                    layout.createSequentialGroup()
                          .addComponent(myTypeLabel)
                          .addComponent(myTextField)
                  )
                  .addComponent(myHierarchyCheckBox)
          );
          layout.setVerticalGroup(
            layout.createSequentialGroup()
                  .addGroup(
                    layout.createParallelGroup(GroupLayout.Alignment.CENTER)
                          .addComponent(myTypeLabel)
                          .addComponent(myTextField)
                  )
                  .addComponent(myHierarchyCheckBox)
          );
        }

        @Override
        protected void loadValues() {
          myTextField.setText((myConstraint.isInvertExprType() ? "!" : "") + myConstraint.getNameOfExprType());
        }

        @Override
        public void saveValues() {
          final String text = myTextField.getText();
          if (text.startsWith("!")) {
            myConstraint.setNameOfExprType(text.substring(1));
            myConstraint.setInvertExprType(true);
          }
          else {
            myConstraint.setNameOfExprType(text);
            myConstraint.setInvertExprType(false);
          }
          myConstraint.setExprTypeWithinHierarchy(myHierarchyCheckBox.isSelected());
        }

        @Override
        public JComponent getPreferredFocusedComponent() {
          return myTextField;
        }

        @Override
        public JComponent[] getFocusableComponents() {
          return new JComponent[] { myTextField };
        }
      };
    }
  }

  private class ReferenceFilter extends FilterAction {

    ReferenceFilter() {
      super("Reference");
    }

    @Override
    public int position() {
      return 4;
    }

    @Override
    protected boolean hasFilter() {
      return !StringUtil.isEmpty(myConstraint.getReferenceConstraint());
    }

    @Override
    protected void clearFilter() {
      myConstraint.setReferenceConstraint("");
      myConstraint.setInvertReference(false);
    }

    @Override
    public boolean isApplicable(List<PsiElement> nodes, boolean completePattern, boolean target) {
      return myProfile.isApplicableConstraint(UIUtil.REFERENCE, nodes, completePattern, target);
    }

    @Override
    protected void setLabel(SimpleColoredComponent component) {
      component.append("reference");
      if (myConstraint.isInvertReference()) component.append("!");
      component.append(myConstraint.getReferenceConstraint());
    }
  }

  private abstract class FilterAction extends AnAction implements Filter {

    protected final SimpleColoredComponent myLabel = new SimpleColoredComponent();

    protected FilterAction(@Nullable String text) {
      super(text);
    }

    public final void init(List<PsiElement> nodes, boolean completePattern, boolean target) {
      if (isApplicable(nodes, completePattern, target) && !myTableModel.getItems().contains(this)) {
        addFilterIfPresent();
      }
      else {
        clearFilter();
        getTemplatePresentation().setEnabledAndVisible(false);
      }
    }

    @Override
    public final void actionPerformed(AnActionEvent e) {
      addFilter();
    }

    @Override
    public final SimpleColoredComponent getRenderer() {
      if (!hasFilter()) removeFilter();
      myLabel.clear();
      setLabel(myLabel);
      return myLabel;
    }

    protected abstract void setLabel(SimpleColoredComponent component);

    protected abstract boolean hasFilter();

    public final void addFilterIfPresent() {
      if (hasFilter()) {
        getTemplatePresentation().setEnabledAndVisible(false);
        if (myTableModel.getRowCount() == 0) {
          myTableModel.addRow(myHeader);
        }
        myTableModel.addRow(this);
      }
      else {
        getTemplatePresentation().setEnabledAndVisible(true);
      }
    }

    public final void addFilter() {
      getTemplatePresentation().setEnabledAndVisible(false);
      final JBTable table = myFilterTable.getTable();
      TableUtil.stopEditing(table);
      table.setRowHeight(table.getRowHeight()); // reset
      int index = 0;
      for (int max = myTableModel.getRowCount(); index < max; index++) {
        if (position() < myTableModel.getItem(index).position()) {
          break;
        }
      }
      if (index == 0) {
        myTableModel.addRow(myHeader);
        index = 1;
      }
      myTableModel.insertRow(index, this);
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

    public final void removeFilter() {
      final int index = myTableModel.indexOf(this);
      if (index >= 0) myTableModel.removeRow(index);
      if (myTableModel.getRowCount() == 1) myTableModel.removeRow(0); // remove header
      getTemplatePresentation().setEnabledAndVisible(true);
      clearFilter();
    }

    protected abstract void clearFilter();

    public abstract boolean isApplicable(List<PsiElement> nodes, boolean completePattern, boolean target);
  }

  abstract static class FilterEditor extends JBTableRowEditor {

    protected final MatchVariableConstraint myConstraint;

    public FilterEditor(MatchVariableConstraint constraint) {
      myConstraint = constraint;
    }

    @Override
    public final JBTableRow getValue() {
      saveValues();
      return __ -> this;
    }

    @Override
    public final void prepareEditor(JTable table, int row) {
      layoutComponents();
      loadValues();
    }

    protected abstract void layoutComponents();

    protected abstract void loadValues();

    protected abstract void saveValues();

  }

  private interface Filter {
    int position();
    SimpleColoredComponent getRenderer();
    default FilterEditor getEditor() {
      return null;
    }
  }
}
