// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui.filters;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.MatchVariableConstraint;
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
public class ReferenceFilter extends FilterAction {

  public ReferenceFilter(FilterTable filterTable) {
    super("Reference", filterTable);
  }

  @Override
  public boolean hasFilter() {
    return !StringUtil.isEmpty(myTable.getConstraint().getReferenceConstraint());
  }

  @Override
  public void clearFilter() {
    final MatchVariableConstraint constraint = myTable.getConstraint();
    constraint.setReferenceConstraint("");
    constraint.setInvertReference(false);
  }

  @Override
  public boolean isApplicable(List<PsiElement> nodes, boolean completePattern, boolean target) {
    return myTable.getProfile().isApplicableConstraint(UIUtil.REFERENCE, nodes, completePattern, target);
  }

  @Override
  protected void setLabel(SimpleColoredComponent component) {
    final MatchVariableConstraint constraint = myTable.getConstraint();
    component.append("reference=");
    if (constraint.isInvertReference()) component.append("!");
    component.append(constraint.getReferenceConstraint());
  }

  @Override
  public FilterEditor getEditor() {
    return new FilterEditor(myTable.getConstraint()) {

      private final JLabel myLabel = new JLabel("reference=");
      private final TextFieldWithAutoCompletion<String> textField =
        TextFieldWithAutoCompletion.create(myTable.getProject(), Collections.emptyList(), false, "");
      private final String shortcut =
        KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(IdeActions.ACTION_CODE_COMPLETION));
      private final ContextHelpLabel myHelpLabel = ContextHelpLabel.create(
        "<p>Preconfigured search patterns can be autocompleted with " +
        shortcut + ".<p>The referenced element is checked against the provided pattern.\n\n" +
        "<p>Use \"!\" to invert the pattern.");

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
