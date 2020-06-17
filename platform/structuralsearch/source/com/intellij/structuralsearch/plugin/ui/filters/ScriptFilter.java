// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui.filters;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.MatchVariableConstraint;
import com.intellij.structuralsearch.NamedScriptableDefinition;
import com.intellij.structuralsearch.SSRBundle;
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
class ScriptFilter extends FilterAction {

  ScriptFilter(FilterTable filterTable) {
    super(SSRBundle.messagePointer("script.filter.name"), filterTable);
  }

  @Override
  public boolean hasFilter() {
    final NamedScriptableDefinition variable = myTable.getVariable();
    return variable != null && !StringUtil.isEmpty(variable.getScriptCodeConstraint());
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
    component.append(SSRBundle.message("script.0.label", StringUtil.unquoteString(myTable.getVariable().getScriptCodeConstraint())));
  }

  @Override
  public FilterEditor getEditor() {
    return new FilterEditor<NamedScriptableDefinition>(myTable.getVariable(), myTable.getConstraintChangedCallback()) {

      private final JLabel myLabel = new JLabel(SSRBundle.message("script.label"));
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

        final String variableText = String.join(", ", variableNames);
        if (myConstraint instanceof MatchVariableConstraint) {
          myHelpLabel = ContextHelpLabel.create(SSRBundle.message("script.filter.match.variable.help.text", variableText));
        }
        else {
          myHelpLabel = ContextHelpLabel.create(SSRBundle.message("script.filter.replacement.variable.help.text", variableText));
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
        return new JComponent[] {myTextField};
      }
    };
  }
}
