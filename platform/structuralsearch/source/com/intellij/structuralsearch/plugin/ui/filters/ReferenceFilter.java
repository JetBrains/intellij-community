// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui.filters;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.MatchVariableConstraint;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.plugin.ui.ConfigurationManager;
import com.intellij.structuralsearch.plugin.ui.UIUtil;
import com.intellij.ui.ContextHelpLabel;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.TextFieldWithAutoCompletion;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
class ReferenceFilter extends FilterAction {

  ReferenceFilter(FilterTable filterTable) {
    super(SSRBundle.messagePointer("reference.filter.name"), filterTable);
  }

  @Override
  public boolean hasFilter() {
    final MatchVariableConstraint variable = myTable.getMatchVariableConstraint();
    return variable != null && !StringUtil.isEmpty(variable.getReferenceConstraint());
  }

  @Override
  public void clearFilter() {
    final MatchVariableConstraint variable = myTable.getMatchVariableConstraint();
    if (variable == null) {
      return;
    }
    variable.setReferenceConstraint("");
    variable.setInvertReference(false);
  }

  @Override
  public boolean isApplicable(List<? extends PsiElement> nodes, boolean completePattern, boolean target) {
    return myTable.getVariable() instanceof MatchVariableConstraint &&
           myTable.getProfile().isApplicableConstraint(UIUtil.REFERENCE, nodes, completePattern, target);
  }

  @Override
  protected void setLabel(SimpleColoredComponent component) {
    final MatchVariableConstraint constraint = myTable.getMatchVariableConstraint();
    final String value = constraint.isInvertReference() ? "!" + constraint.getReferenceConstraint() : constraint.getReferenceConstraint();
    component.append(SSRBundle.message("reference.0.label", value));
  }

  @Override
  public FilterEditor<MatchVariableConstraint> getEditor() {
    return new FilterEditor<MatchVariableConstraint>(myTable.getMatchVariableConstraint(), myTable.getConstraintChangedCallback()) {

      private final JLabel myLabel = new JLabel(SSRBundle.message("reference.label"));
      private final TextFieldWithAutoCompletion<String> textField =
        TextFieldWithAutoCompletion.create(myTable.getProject(), Collections.emptyList(), false, "");
      private final String shortcut =
        KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(IdeActions.ACTION_CODE_COMPLETION));
      private final ContextHelpLabel myHelpLabel = ContextHelpLabel.create(SSRBundle.message("reference.filter.help.text", shortcut));

      @Override
      protected void layoutComponents() {
        textField.setVariants(ConfigurationManager.getInstance(myTable.getProject()).getAllConfigurationNames());
        final GroupLayout layout = new GroupLayout(this);
        setLayout(layout);
        layout.setAutoCreateContainerGaps(true);

        layout.setHorizontalGroup(
          layout.createSequentialGroup()
                .addComponent(myLabel)
                .addComponent(textField)
                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, 1, 1)
                .addComponent(myHelpLabel)
        );
        layout.setVerticalGroup(
          layout.createParallelGroup(GroupLayout.Alignment.CENTER)
                .addComponent(myLabel)
                .addComponent(textField)
                .addComponent(myHelpLabel)
        );
      }

      @Override
      protected void loadValues() {
        textField.setText((myConstraint.isInvertReference() ? "!" : "") + myConstraint.getReferenceConstraint());
      }

      @Override
      protected void saveValues() {
        final String text = textField.getText();
        if (text.startsWith("!")) {
          myConstraint.setReferenceConstraint(text.substring(1));
          myConstraint.setInvertReference(true);
        }
        else {
          myConstraint.setReferenceConstraint(text);
          myConstraint.setInvertReference(false);
        }
      }

      @Override
      public JComponent getPreferredFocusedComponent() {
        return textField;
      }

      @Override
      public JComponent[] getFocusableComponents() {
        return new JComponent[]{textField};
      }
    };
  }
}
