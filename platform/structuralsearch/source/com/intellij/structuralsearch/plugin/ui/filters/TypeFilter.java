// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui.filters;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.MatchVariableConstraint;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.StructuralSearchProfile;
import com.intellij.structuralsearch.plugin.ui.UIUtil;
import com.intellij.ui.ContextHelpLabel;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import org.intellij.lang.regexp.RegExpFileType;

import javax.swing.*;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class TypeFilter extends FilterAction {

  boolean myShowRegex;

  public TypeFilter() {
    super(SSRBundle.messagePointer("type.filter.name"));
  }

  @Override
  public boolean hasFilter() {
    final MatchVariableConstraint variable = myTable.getMatchVariable();
    return variable != null && !StringUtil.isEmpty(variable.getNameOfExprType());
  }

  @Override
  public void clearFilter() {
    final MatchVariableConstraint variable = myTable.getMatchVariable();
    if (variable == null) {
      return;
    }
    variable.setNameOfExprType("");
    variable.setInvertExprType(false);
    variable.setExprTypeWithinHierarchy(false);
  }

  @Override
  public boolean isApplicable(List<? extends PsiElement> nodes, boolean completePattern, boolean target) {
    if (myTable.getMatchVariable() == null) {
      return false;
    }
    final StructuralSearchProfile profile = myTable.getProfile();
    myShowRegex = profile.isApplicableConstraint(UIUtil.TYPE_REGEX, nodes, completePattern, target);
    return profile.isApplicableConstraint(UIUtil.TYPE, nodes, completePattern, target);
  }

  @Override
  protected void setLabel(SimpleColoredComponent component) {
    final MatchVariableConstraint variable = myTable.getMatchVariable();
    if (variable == null) {
      return;
    }
    final String s = variable.isRegexExprType() ? variable.getNameOfExprType() : variable.getExpressionTypes();
    final String value = variable.isInvertExprType() ? "!" + s : s;
    myLabel.append(SSRBundle.message("type.0.label", value));
    if (variable.isExprTypeWithinHierarchy()) {
      myLabel.append(SSRBundle.message("within.hierarchy.label"), SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }
  }

  @Override
  public FilterEditor<MatchVariableConstraint> getEditor() {
    return new FilterEditor<MatchVariableConstraint>(myTable.getMatchVariable(), myTable.getConstraintChangedCallback()) {

      private final EditorTextField myTextField = UIUtil.createTextComponent("", myTable.getProject());
      private final JLabel myTypeLabel = new JLabel(SSRBundle.message("type.label"));
      private final JCheckBox myHierarchyCheckBox = new JCheckBox(SSRBundle.message("within.type.hierarchy.check.box"), false);
      private final JCheckBox myRegexCheckBox = new JCheckBox(SSRBundle.message("regex.check.box"), false);
      {
        myRegexCheckBox.addActionListener(e -> {
          final FileType fileType = myRegexCheckBox.isSelected() ? RegExpFileType.INSTANCE : PlainTextFileType.INSTANCE;
          final Document document = UIUtil.createDocument(fileType, myTextField.getText(), myTable.getProject());
          myTextField.setDocument(document);
        });
      }
      private final ContextHelpLabel myHelpLabel = ContextHelpLabel.create(SSRBundle.message("type.filter.help.text"));

      @Override
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
                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, 1, 1)
                .addComponent(myHelpLabel)
            )
            .addGroup(
              layout.createSequentialGroup()
                .addComponent(myHierarchyCheckBox)
                .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(myRegexCheckBox)
            )
        );
        layout.setVerticalGroup(
          layout.createSequentialGroup()
            .addGroup(
              layout.createParallelGroup(GroupLayout.Alignment.CENTER)
                .addComponent(myTypeLabel)
                .addComponent(myTextField)
                .addComponent(myHelpLabel)
            )
            .addGroup(
              layout.createParallelGroup(GroupLayout.Alignment.CENTER)
                .addComponent(myHierarchyCheckBox)
                .addComponent(myRegexCheckBox)
            )
        );
      }

      @Override
      protected void loadValues() {
        final boolean regex = myConstraint.isRegexExprType();
        myTextField.setFileType(myShowRegex && regex ? RegExpFileType.INSTANCE : PlainTextFileType.INSTANCE);
        myTextField.setText((myConstraint.isInvertExprType() ? "!" : "") +
                            (regex ? myConstraint.getNameOfExprType() : myConstraint.getExpressionTypes()))  ;
        myHierarchyCheckBox.setSelected(myConstraint.isExprTypeWithinHierarchy());
        myRegexCheckBox.setSelected(myShowRegex && regex);
        myRegexCheckBox.setVisible(myShowRegex);
      }

      @Override
      public void saveValues() {
        String text = myTextField.getText();
        final boolean inverted = text.startsWith("!");
        if (inverted) {
          text = text.substring(1);
        }
        if (myShowRegex && myRegexCheckBox.isSelected()) {
          myConstraint.setNameOfExprType(text);
        }
        else {
          myConstraint.setExpressionTypes(text);
        }
        myConstraint.setInvertExprType(inverted);
        myConstraint.setExprTypeWithinHierarchy(myHierarchyCheckBox.isSelected());
      }

      @Override
      public JComponent getPreferredFocusedComponent() {
        return myTextField;
      }

      @Override
      public JComponent[] getFocusableComponents() {
        return new JComponent[]{myTextField};
      }
    };
  }
}
