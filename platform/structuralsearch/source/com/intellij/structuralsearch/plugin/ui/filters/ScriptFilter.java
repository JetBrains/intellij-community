// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui.filters;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.MatchVariableConstraint;
import com.intellij.structuralsearch.NamedScriptableDefinition;
import com.intellij.structuralsearch.impl.matcher.predicates.ScriptLog;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.UIUtil;
import com.intellij.ui.ContextHelpLabel;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.ExpandableEditorSupport;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
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
    return !StringUtil.isEmpty(myTable.getVariable().getScriptCodeConstraint());
  }

  @Override
  public void clearFilter() {
    myTable.getVariable().setScriptCodeConstraint("");
  }

  @Override
  public boolean isApplicable(List<? extends PsiElement> nodes, boolean completePattern, boolean target) {
    return true;
  }

  @Override
  protected void setLabel(SimpleColoredComponent component) {
    component.append("script=").append(StringUtil.unquoteString(myTable.getVariable().getScriptCodeConstraint()));
  }

  @Override
  public FilterEditor getEditor() {
    return new FilterEditor<NamedScriptableDefinition>(myTable.getVariable(), myTable.getConstraintChangedCallback()) {

      private final JLabel myLabel = new JLabel("script=");
      private final EditorTextField myTextField = UIUtil.createScriptComponent("", myTable.getProject());
      private ContextHelpLabel myHelpLabel;

      @Override
      protected void layoutComponents() {
        new ExpandableEditorSupport(myTextField) {
          @NotNull
          @Override
          protected Content prepare(@NotNull EditorTextField field, @NotNull Function<? super String, String> onShow) {
            final Content popup = super.prepare(field, onShow);
            popup.getContentComponent().setPreferredSize(new Dimension(600, 150));
            return popup;
          }
        };
        final String[] variableNames = {Configuration.CONTEXT_VAR_NAME, ScriptLog.SCRIPT_LOG_VAR_NAME};

        final String variableText = "<p>Available variables: " + String.join(", ", variableNames);
        if (myConstraint instanceof MatchVariableConstraint) {
          myHelpLabel = ContextHelpLabel.create(
            "<p>Use the GroovyScript IntelliJ API to filter the search results. " +
            "When the specified script returns <code>false</code>, the found element will not be in the search results. " +
            "Non-boolean script results will be converted to boolean." + variableText);
        }
        else {
          myHelpLabel = ContextHelpLabel.create(
            "<p>Use the GroovyScript Intellij API to create a custom replacement, for advanced renaming, rewriting or refactoring. " +
            "When replacing, the variable in the replace template will be replaced with the String result of the specified script. " +
            variableText);
        }

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
