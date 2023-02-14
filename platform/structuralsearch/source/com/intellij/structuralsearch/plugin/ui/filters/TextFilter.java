// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui.filters;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.MatchVariableConstraint;
import com.intellij.structuralsearch.NamedScriptableDefinition;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.plugin.ui.UIUtil;
import com.intellij.ui.ContextHelpLabel;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
@SuppressWarnings("ComponentNotRegistered")
public class TextFilter extends FilterAction {

  boolean myShowHierarchy;

  public TextFilter() {
    super(SSRBundle.messagePointer("text.filter.name"));
  }

  @Override
  public @NotNull String getShortText(NamedScriptableDefinition variable) {
    if (!(variable instanceof MatchVariableConstraint constraint)) {
      return "";
    }
    final String text = constraint.getRegExp();
    if (text.isEmpty()) {
      return constraint.isWithinHierarchy() ? SSRBundle.message("hierarchy.tooltip.message") : "";
    }
    return SSRBundle.message("text.tooltip.message",
                             constraint.isInvertRegExp() ? 1 : 0,
                             text,
                             constraint.isWholeWordsOnly() ? 1 : 0,
                             constraint.isWithinHierarchy() ? 1 : 0);
  }

  @Override
  public boolean hasFilter() {
    final MatchVariableConstraint variable = myTable.getMatchVariable();
    return variable != null && (!StringUtil.isEmpty(variable.getRegExp()) || variable.isWithinHierarchy());
  }

  @Override
  public void clearFilter() {
    final MatchVariableConstraint variable = myTable.getMatchVariable();
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
    myShowHierarchy = isApplicableConstraint(UIUtil.TEXT_HIERARCHY, nodes, completePattern, target);
    return isApplicableConstraint(UIUtil.TEXT, nodes, completePattern, target);
  }

  @Override
  protected void setLabel(SimpleColoredComponent component) {
    final MatchVariableConstraint variable = myTable.getMatchVariable();
    if (variable == null) {
      return;
    }
    final String value = variable.isInvertRegExp() ? "!" + variable.getRegExp() : variable.getRegExp();
    myLabel.append(SSRBundle.message("text.0.label", value));
    if (variable.isWholeWordsOnly()) myLabel.append(SSRBundle.message("whole.words.label"), SimpleTextAttributes.GRAYED_ATTRIBUTES);
    if (variable.isWithinHierarchy()) myLabel.append(SSRBundle.message("within.hierarchy.label"), SimpleTextAttributes.GRAYED_ATTRIBUTES);
  }

  @Override
  public FilterEditor<MatchVariableConstraint> getEditor() {
    return new FilterEditor<>(myTable.getMatchVariable(), myTable.getConstraintChangedCallback()) {

      private final EditorTextField myTextField = UIUtil.createRegexComponent("", myTable.getProject());
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
                .addComponent(myHierarchyCheckBox)
            )
        );
      }

      @Override
      protected void loadValues() {
        myTextField.setText((myConstraint.isInvertRegExp() ? "!" : "") + myConstraint.getRegExp());
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
        myConstraint.setWholeWordsOnly(false);
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
