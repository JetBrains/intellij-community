// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.structuralsearch.MatchVariableConstraint;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.table.JBTable;
import com.intellij.ui.table.TableView;
import com.intellij.util.SmartList;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.table.JBListTable;
import com.intellij.util.ui.table.JBTableRow;
import com.intellij.util.ui.table.JBTableRowEditor;
import com.intellij.util.ui.table.JBTableRowRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class FilterPanel {

  private final JPanel myFilterPanel;
  private final JBListTable myFilterTable;
  private final ListTableModel<Filter> myTableModel;
  @NotNull private final Project myProject;
  private MatchVariableConstraint myConstraint;

  public FilterPanel(@NotNull Project project, Disposable parent) {
    myProject = project;
    myTableModel = new ListTableModel<>(new ColumnInfo<Filter, Filter>("") {
      @Nullable
      @Override
      public Filter valueOf(Filter s) {
        return s;
      }
    });
    myFilterTable = new JBListTable(new TableView<>(myTableModel), parent) {

      @Override
      protected JBTableRowRenderer getRowRenderer(int row) {
        return new JBTableRowRenderer() {
          @Override
          public JComponent getRowRendererComponent(JTable table, int row, boolean selected, boolean focused) {
            return myTableModel.getRowValue(row).getRowRendererComponent();
          }
        };
      }

      @Override
      protected JBTableRowEditor getRowEditor(int row) {
        return myTableModel.getRowValue(row).getRowEditor();
      }
    };
    final JBTable table = myFilterTable.getTable();
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
                                    .createPanel();
    myFilterPanel.setPreferredSize(new Dimension(350, 40));
    myFilterPanel.setVisible(false);
    myFilterPanel.setBorder(com.intellij.util.ui.UIUtil.getTextFieldBorder());
  }

  private void showAddFilterPopup(Component component, RelativePoint point) {
    final DefaultActionGroup group = new DefaultActionGroup(
      new AnAction("Text") {
        @Override
        public void actionPerformed(AnActionEvent e) {
          myTableModel.addRow(new TextFilter(myConstraint));
        }
      },
      new AnAction("Type") {
        @Override
        public void actionPerformed(AnActionEvent e) {
          assert false : "not implemented";
        }
      },
      new AnAction("Reference") {
        @Override
        public void actionPerformed(AnActionEvent e) {
          assert false : "not implemented";
        }
      },
      new AnAction("Script") {
        @Override
        public void actionPerformed(AnActionEvent e) {
          assert false : "not implemented";
        }
      }
    );
    final DataContext context = DataManager.getInstance().getDataContext(component);
    final ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup("Add Filter", group, context,
                                                                                JBPopupFactory.ActionSelectionAid.ALPHA_NUMBERING, true, null);
    popup.show(point);
  }

  public JComponent getComponent() {
    return myFilterPanel;
  }

  public void setFilters(MatchVariableConstraint constraint) {
    myConstraint = constraint;
    final List<Filter> items = new SmartList<>();
    final String name = constraint.getName();
    items.add(() -> {
      final SimpleColoredComponent label = new SimpleColoredComponent();
      label.append("Filters for $" + name + "$:", SimpleTextAttributes.GRAYED_ATTRIBUTES);
      return label;
    });
    if (!StringUtil.isEmpty(constraint.getRegExp())) {
      items.add(new TextFilter(constraint));
    }
    final int min = constraint.getMinCount();
    final int max = constraint.getMaxCount();
    if (min != 1 || max != 1) {
      items.add(() -> new SimpleColoredComponent().append("count=[" + min + "," + (max == Integer.MAX_VALUE ? "∞" : max) + ']'));
    }

    if (items.size() <= 1) {
      items.clear();
      final String message;
      if (Configuration.CONTEXT_VAR_NAME.equals(name)) {
        message = "No filters added for the complete match.";
      }
      else {
        message = "No Filters added for $" + name + "$.";
      }
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
    myTableModel.setItems(items);
  }

  private EditorTextField createTextComponent(String text) {
    return createEditorComponent(text, "1.txt");
  }

  private EditorTextField createRegexComponent(String text) {
    return createEditorComponent(text, "1.regexp");
  }

  private EditorTextField createScriptComponent(String text) {
    return createEditorComponent(text, "1.groovy");
  }

  @NotNull
  private EditorTextField createEditorComponent(String text, String fileName) {
    return new EditorTextField(text, myProject, getFileType(fileName));
  }

  private Document createDocument(final String fileName, final FileType fileType, String text) {
    final PsiFile file = PsiFileFactory.getInstance(myProject).createFileFromText(fileName, fileType, text, -1, true);
    return PsiDocumentManager.getInstance(myProject).getDocument(file);
  }

  private static FileType getFileType(final String fileName) {
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
    if (fileType == FileTypes.UNKNOWN) fileType = FileTypes.PLAIN_TEXT;
    return fileType;
  }

  private interface Filter {
    JComponent getRowRendererComponent();
    default JBTableRowEditor getRowEditor() {
      return null;
    }
  }

  private class TextFilter implements Filter {

    private final MatchVariableConstraint myConstraint;

    public TextFilter(MatchVariableConstraint constraint) {
      myConstraint = constraint;
    }

    @Override
    public JComponent getRowRendererComponent() {
      final SimpleColoredComponent component = new SimpleColoredComponent();
      component.append("text=" + myConstraint.getRegExp());
      if (myConstraint.isWholeWordsOnly()) component.append(", whole words", SimpleTextAttributes.GRAYED_ATTRIBUTES);
      if (myConstraint.isWithinHierarchy()) component.append(", within hierarchy", SimpleTextAttributes.GRAYED_ATTRIBUTES);
      return component;
    }

    @Override
    public JBTableRowEditor getRowEditor() {
      return new JBTableRowEditor() {

        private final EditorTextField myTextField = createRegexComponent(myConstraint.getRegExp());
        private final JCheckBox myWordsCheckBox = new JCheckBox("Words", myConstraint.isWholeWordsOnly());
        private final JCheckBox myHierarchyCheckBox = new JCheckBox("Within type hierarchy", myConstraint.isWithinHierarchy());

        @Override
        public void prepareEditor(JTable table, int row) {
          final JLabel nameLabel = new JLabel("name=");
          final ContextHelpLabel helpLabel = ContextHelpLabel.create("Text of the match is checked against the provided pattern.");

          final GroupLayout layout = new GroupLayout(this);
          setLayout(layout);
          layout.setAutoCreateContainerGaps(true);

          layout.setHorizontalGroup(
            layout.createParallelGroup()
                  .addGroup(
                    layout.createSequentialGroup()
                          .addComponent(nameLabel)
                          .addComponent(myTextField)
                          .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, 1, 1)
                          .addComponent(helpLabel)
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
                          .addComponent(nameLabel)
                          .addComponent(myTextField)
                          .addComponent(helpLabel)
                  )
                  .addGroup(
                    layout.createParallelGroup(GroupLayout.Alignment.CENTER)
                          .addComponent(myWordsCheckBox)
                          .addComponent(myHierarchyCheckBox)
                  )
          );
        }

        @Override
        public JBTableRow getValue() {
          myConstraint.setRegExp(myTextField.getText());
          myConstraint.setWholeWordsOnly(myWordsCheckBox.isSelected());
          myConstraint.setWithinHierarchy(myHierarchyCheckBox.isSelected());
          return __ -> this;
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
}
