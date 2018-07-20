// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui.filters;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.MatchVariableConstraint;
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
public class TextFilter extends FilterAction {

  boolean showHierarchy;

  public TextFilter(FilterTable filterTable) {
    super("Text", filterTable);
  }

  @Override
  public boolean hasFilter() {
    return !StringUtil.isEmpty(myTable.getConstraint().getRegExp());
  }

  public void clearFilter() {
    final MatchVariableConstraint constraint = myTable.getConstraint();
    constraint.setRegExp("");
    constraint.setWholeWordsOnly(false);
    constraint.setWithinHierarchy(false);
  }

  @Override
  public boolean isApplicable(List<PsiElement> nodes, boolean completePattern, boolean target) {
    final StructuralSearchProfile profile = myTable.getProfile();
    showHierarchy = profile.isApplicableConstraint(UIUtil.TEXT_HIERARCHY, nodes, completePattern, target);
    return profile.isApplicableConstraint(UIUtil.TEXT, nodes, completePattern, target);
  }

  @Override
  protected void setLabel(SimpleColoredComponent component) {
    final MatchVariableConstraint constraint = myTable.getConstraint();
    myLabel.append("text=");
    if (constraint.isInvertRegExp()) myLabel.append("!");
    myLabel.append(constraint.getRegExp());
    if (constraint.isWholeWordsOnly()) myLabel.append(", whole words", SimpleTextAttributes.GRAYED_ATTRIBUTES);
    if (constraint.isWithinHierarchy()) myLabel.append(", within hierarchy", SimpleTextAttributes.GRAYED_ATTRIBUTES);
  }

  @Override
  public FilterEditor getEditor() {
    return new FilterEditor(myTable.getConstraint()) {

      private final EditorTextField myTextField = UIUtil.createRegexComponent("", myTable.getProject());
      private final JCheckBox myWordsCheckBox = new JCheckBox("Words", false);
      private final JCheckBox myHierarchyCheckBox = new JCheckBox("Within type hierarchy", false);
      private final JLabel myNameLabel = new JLabel("name=");
      private final ContextHelpLabel myHelpLabel =
        ContextHelpLabel.create("<p>Text of the match is checked against the provided pattern." +
                                "<p>Use \"!\" to invert the pattern." +
                                "<p>Regular expressions are supported.");

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
        myTextField.setText((myConstraint.isInvertRegExp() ? "!" : "") + myConstraint.getRegExp());
        myWordsCheckBox.setSelected(myConstraint.isWholeWordsOnly());
        myHierarchyCheckBox.setSelected(myConstraint.isWithinHierarchy());
        myHierarchyCheckBox.setVisible(showHierarchy);
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
