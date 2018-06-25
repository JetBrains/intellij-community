// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui.filters;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.MatchVariableConstraint;
import com.intellij.structuralsearch.plugin.ui.UIUtil;
import com.intellij.ui.ContextHelpLabel;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class TypeFilter extends FilterAction {

  public TypeFilter(FilterTable filterTable) {
    super("Type", filterTable);
  }

  @Override
  public boolean hasFilter() {
    return !StringUtil.isEmpty(myTable.getConstraint().getNameOfExprType());
  }

  @Override
  public void clearFilter() {
    final MatchVariableConstraint constraint = myTable.getConstraint();
    constraint.setNameOfExprType("");
    constraint.setInvertExprType(false);
    constraint.setExprTypeWithinHierarchy(false);
  }

  @Override
  public boolean isApplicable(List<PsiElement> nodes, boolean completePattern, boolean target) {
    return myTable.getProfile().isApplicableConstraint(UIUtil.TYPE, nodes, completePattern, target);
  }

  @Override
  protected void setLabel(SimpleColoredComponent component) {
    final MatchVariableConstraint constraint = myTable.getConstraint();
    myLabel.append("type=");
    if (constraint.isInvertExprType()) myLabel.append("!");
    myLabel.append(constraint.getNameOfExprType());
    if (constraint.isExprTypeWithinHierarchy()) myLabel.append(", within hierarchy", SimpleTextAttributes.GRAYED_ATTRIBUTES);
  }

  @Override
  public FilterEditor getEditor() {
    return new FilterEditor(myTable.getConstraint()) {

      private final EditorTextField myTextField = UIUtil.createTextComponent("", myTable.getProject());
      private final JLabel myTypeLabel = new JLabel("type=");
      private final JCheckBox myHierarchyCheckBox = new JCheckBox("Within type hierarchy", false);
      private final ContextHelpLabel myHelpLabel = ContextHelpLabel.create(
        "<p>The type of the matched expression is checked against the provided \"|\"-separated patterns. " +
        "<p>Use \"!\" to invert the pattern.");

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
                        .addComponent(myHelpLabel)
                )
                .addComponent(myHierarchyCheckBox)
        );
        layout.setVerticalGroup(
          layout.createSequentialGroup()
                .addGroup(
                  layout.createParallelGroup(GroupLayout.Alignment.CENTER)
                        .addComponent(myTypeLabel)
                        .addComponent(myTextField)
                        .addComponent(myHelpLabel)
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
        return new JComponent[]{myTextField};
      }
    };
  }
}
