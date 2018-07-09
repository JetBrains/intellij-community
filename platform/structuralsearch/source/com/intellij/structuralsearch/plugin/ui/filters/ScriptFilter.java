// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui.filters;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.predicates.ScriptLog;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.ui.ContextHelpLabel;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.components.fields.ExpandableTextField;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class ScriptFilter extends FilterAction {

  public ScriptFilter(FilterTable filterTable) {
    super("Script", filterTable);
  }

  @Override
  public boolean hasFilter() {
    return !StringUtil.isEmpty(myTable.getConstraint().getScriptCodeConstraint());
  }

  @Override
  public void clearFilter() {
    myTable.getConstraint().setScriptCodeConstraint("");
  }

  @Override
  public boolean isApplicable(List<PsiElement> nodes, boolean completePattern, boolean target) {
    return true;
  }

  @Override
  protected void setLabel(SimpleColoredComponent component) {
    component.append("script=").append(StringUtil.unquoteString(myTable.getConstraint().getScriptCodeConstraint()));
  }

  @Override
  public FilterEditor getEditor() {
    return new FilterEditor(myTable.getConstraint()) {

      private final JLabel myLabel = new JLabel("script=");
      private final ExpandableTextField myTextField = new ExpandableTextField(a -> Collections.singletonList(a), a -> a.get(0));
      private ContextHelpLabel myHelpLabel;

      @Override
      protected void layoutComponents() {
        final String[] variableNames = {Configuration.CONTEXT_VAR_NAME, ScriptLog.SCRIPT_LOG_VAR_NAME};
        myHelpLabel = ContextHelpLabel.create(
          "<p>Use GroovyScript IntelliJ API to filter the search results." +
          "<p>Available variables: " + String.join(", ", variableNames));

        final GroupLayout layout = new GroupLayout(this);
        setLayout(layout);
        layout.setAutoCreateContainerGaps(true);

        layout.setHorizontalGroup(
          layout.createSequentialGroup()
                .addComponent(myLabel)
                .addComponent(myTextField)
                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, 1, 1)
                .addComponent(myHelpLabel)
        );
        layout.setVerticalGroup(
          layout.createParallelGroup(GroupLayout.Alignment.CENTER)
                .addComponent(myLabel)
                .addComponent(myTextField)
                .addComponent(myHelpLabel)
        );
      }

      @Override
      protected void loadValues() {
        myTextField.setText(StringUtil.unquoteString(myConstraint.getScriptCodeConstraint()));
      }

      @Override
      protected void saveValues() {
        myConstraint.setScriptCodeConstraint('"' + myTextField.getText() + '"');
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
