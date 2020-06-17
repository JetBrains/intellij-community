// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui.filters;

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

import javax.swing.*;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
class TextFilter extends FilterAction {

  boolean myShowHierarchy;

  TextFilter(FilterTable filterTable) {
    super(SSRBundle.messagePointer("text.filter.name"), filterTable);
  }

  @Override
  public boolean hasFilter() {
    final MatchVariableConstraint variable = myTable.getMatchVariableConstraint();
    return variable != null && (!StringUtil.isEmpty(variable.getRegExp()) || variable.isWithinHierarchy());
  }

  @Override
  public void clearFilter() {
    final MatchVariableConstraint variable = myTable.getMatchVariableConstraint();
    if (variable == null) {
      return;
    }
    variable.setRegExp("");
    variable.setWholeWordsOnly(false);
    variable.setWithinHierarchy(false);
  }

  @Override
  public boolean isApplicable(List<? extends PsiElement> nodes, boolean completePattern, boolean target) {
    if (!(myTable.getVariable() instanceof MatchVariableConstraint)) {
      return false;
    }
    final StructuralSearchProfile profile = myTable.getProfile();
    myShowHierarchy = profile.isApplicableConstraint(UIUtil.TEXT_HIERARCHY, nodes, completePattern, target);
    return profile.isApplicableConstraint(UIUtil.TEXT, nodes, completePattern, target);
  }

  @Override
  protected void setLabel(SimpleColoredComponent component) {
    final MatchVariableConstraint constraint = myTable.getMatchVariableConstraint();
    String value = constraint.isInvertRegExp() ? "!" + constraint.getRegExp() : constraint.getRegExp();
    myLabel.append(SSRBundle.message("text.0.label", value));
    if (constraint.isWholeWordsOnly()) myLabel.append(SSRBundle.message("whole.words.label"), SimpleTextAttributes.GRAYED_ATTRIBUTES);
    if (constraint.isWithinHierarchy()) myLabel.append(SSRBundle.message("within.hierarchy.label"), SimpleTextAttributes.GRAYED_ATTRIBUTES);
  }

  @Override
  public FilterEditor<MatchVariableConstraint> getEditor() {
    return new FilterEditor<MatchVariableConstraint>(myTable.getMatchVariableConstraint(), myTable.getConstraintChangedCallback()) {

      private final EditorTextField myTextField = UIUtil.createRegexComponent("", myTable.getProject());
      private final JCheckBox myWordsCheckBox = new JCheckBox(SSRBundle.message("whole.words.check.box"), false);
      private final JCheckBox myHierarchyCheckBox = new JCheckBox(SSRBundle.message("within.type.hierarchy.check.box"), false);
      private final JLabel myTextLabel = new JLabel(SSRBundle.message("text.label"));
      private final ContextHelpLabel myHelpLabel = ContextHelpLabel.create(SSRBundle.message("text.filter.help.text"));

      @Override
      protected void layoutComponents() {
        final GroupLayout layout = new GroupLayout(this);
        setLayout(layout);
        layout.setAutoCreateContainerGaps(true);

        layout.setHorizontalGroup(
          layout.createParallelGroup()
                .addGroup(
                  layout.createSequentialGroup()
                        .addComponent(myTextLabel)
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
                        .addComponent(myTextLabel)
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
        myTextField.setText((myConstraint.isInvertRegExp() ? "!" : "") + myConstraint.getRegExp());
        myWordsCheckBox.setSelected(myConstraint.isWholeWordsOnly());
        myHierarchyCheckBox.setSelected(myConstraint.isWithinHierarchy());
        myHierarchyCheckBox.setVisible(myShowHierarchy);
      }

      @Override
      public void saveValues() {
        final String text = myTextField.getText();
        if (text.startsWith("!")) {
          myConstraint.setRegExp(text.substring(1));
          myConstraint.setInvertRegExp(true);
        }
        else {
          myConstraint.setRegExp(text);
          myConstraint.setInvertRegExp(false);
        }
        myConstraint.setWholeWordsOnly(myWordsCheckBox.isSelected());
        myConstraint.setWithinHierarchy(myHierarchyCheckBox.isSelected());
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
