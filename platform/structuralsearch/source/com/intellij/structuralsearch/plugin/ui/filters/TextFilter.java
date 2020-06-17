// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui.filters;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.MatchVariableConstraint;
import com.intellij.structuralsearch.NamedScriptableDefinition;
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
    final NamedScriptableDefinition variable = myTable.getVariable();
    if (!(variable instanceof MatchVariableConstraint)) {
      return false;
    }
    final MatchVariableConstraint constraint = (MatchVariableConstraint)variable;
    return !StringUtil.isEmpty(constraint.getRegExp()) || constraint.isWithinHierarchy();
  }

  @Override
  public void clearFilter() {
    final NamedScriptableDefinition variable = myTable.getVariable();
    if (!(variable instanceof MatchVariableConstraint)) {
      return;
    }
    final MatchVariableConstraint constraint = (MatchVariableConstraint)variable;
    constraint.setRegExp("");
    constraint.setWholeWordsOnly(false);
    constraint.setWithinHierarchy(false);
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
    final MatchVariableConstraint constraint = (MatchVariableConstraint)myTable.getVariable();
    String value = constraint.isInvertRegExp() ? "!" + constraint.getRegExp() : constraint.getRegExp();
    myLabel.append(SSRBundle.message("text.0.label", value));
    if (constraint.isWholeWordsOnly()) myLabel.append(SSRBundle.message("whole.words.label"), SimpleTextAttributes.GRAYED_ATTRIBUTES);
    if (constraint.isWithinHierarchy()) myLabel.append(SSRBundle.message("within.hierarchy.label"), SimpleTextAttributes.GRAYED_ATTRIBUTES);
  }

  @Override
  public FilterEditor getEditor() {
    return new FilterEditor<MatchVariableConstraint>(myTable.getVariable(), myTable.getConstraintChangedCallback()) {

      private final EditorTextField myTextField = UIUtil.createRegexComponent("", myTable.getProject());
      private final JCheckBox myWordsCheckBox = new JCheckBox(SSRBundle.message("whole.words.check.box"), false);
      private final JCheckBox myHierarchyCheckBox = new JCheckBox(SSRBundle.message("within.type.hierarchy.check.box"), false);
      private final JLabel myTextLabel = new JLabel(SSRBundle.message("text.label"));
      private final ContextHelpLabel myHelpLabel =
        ContextHelpLabel.create(SSRBundle.message("text.filter.help.text"));

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
