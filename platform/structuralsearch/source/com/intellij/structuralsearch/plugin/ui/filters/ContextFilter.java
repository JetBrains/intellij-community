 // Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui.filters;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.MatchVariableConstraint;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.ConfigurationManager;
import com.intellij.structuralsearch.plugin.ui.UIUtil;
import com.intellij.ui.ContextHelpLabel;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.util.containers.JBIterable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

class ContextFilter extends FilterAction {
  ContextFilter(FilterTable table) {
    super(SSRBundle.messagePointer("context.filter.name"), table);
  }

  @Override
  public boolean hasFilter() {
    final MatchVariableConstraint variable = myTable.getMatchVariable();
    return variable != null && !StringUtil.isEmpty(variable.getContextConstraint());
  }

  @Override
  public void clearFilter() {
    final MatchVariableConstraint constraint = myTable.getMatchVariable();
    if (constraint == null) {
      return;
    }
    constraint.setContextConstraint("");
  }

  @Override
  public boolean isApplicable(List<? extends PsiElement> nodes, boolean completePattern, boolean target) {
    return myTable.getVariable() instanceof MatchVariableConstraint && completePattern &&
           myTable.getProfile().isApplicableConstraint(UIUtil.CONTEXT, nodes, true, target);
  }

  @Override
  protected void setLabel(SimpleColoredComponent component) {
    final MatchVariableConstraint variable = myTable.getMatchVariable();
    if (variable == null) {
      return;
    }
    component.append(SSRBundle.message("context.0.label", StringUtil.unquoteString(variable.getContextConstraint())));
  }

  @Override
  public FilterEditor<MatchVariableConstraint> getEditor() {
    return new FilterEditor<MatchVariableConstraint>(myTable.getMatchVariable(), myTable.getConstraintChangedCallback()) {
      private final JLabel myLabel = new JLabel(SSRBundle.message("context.label"));
      private final TextFieldWithAutoCompletion<String> textField =
        TextFieldWithAutoCompletion.create(myTable.getProject(), Collections.emptyList(), false, "");
      private final String shortcut =
        KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(IdeActions.ACTION_CODE_COMPLETION));
      private final ContextHelpLabel myHelpLabel = ContextHelpLabel.create(SSRBundle.message("tooltip.preconfigured.search.patterns", shortcut));

      @Override
      protected void layoutComponents() {
        final ConfigurationManager configManager = ConfigurationManager.getInstance(myTable.getProject());
        final List<String> configurationNames = JBIterable
          .from(configManager.getAllConfigurationNames())
          .filter(name -> {
            final Configuration config = configManager.findConfigurationByName(name);
            return config != null && myTable.getProfile().isApplicableContextConfiguration(config);
          })
          .toList();
        textField.setVariants(configurationNames);
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
        textField.setText(myConstraint.getContextConstraint());
      }

      @Override
      protected void saveValues() {
        myConstraint.setContextConstraint(textField.getText());
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