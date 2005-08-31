/*
 * Copyright 2003-2005 Dave Griffith
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.errorhandling;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.jsp.JspFile;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

public class EmptyCatchBlockInspection extends StatementInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_includeComments = true;
  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreTestCases = true;

  public String getGroupDisplayName() {
    return GroupNames.ERRORHANDLING_GROUP_NAME;
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  public JComponent createOptionsPanel() {
    final GridBagLayout layout = new GridBagLayout();
    final JPanel panel = new JPanel(layout);
    final JCheckBox commentsCheckBox = new JCheckBox(InspectionGadgetsBundle.message("empty.catch.block.comments.option"), m_includeComments);
    final ButtonModel commentsModel = commentsCheckBox.getModel();
    commentsModel.addChangeListener(new ChangeListener() {

      public void stateChanged(ChangeEvent e) {
        m_includeComments = commentsModel.isSelected();
      }
    });
    final JCheckBox testCaseCheckBox = new JCheckBox(InspectionGadgetsBundle.message("empty.catch.block.ignore.option"), m_ignoreTestCases);
    final ButtonModel model = testCaseCheckBox.getModel();
    model.addChangeListener(new ChangeListener() {

      public void stateChanged(ChangeEvent e) {
        m_ignoreTestCases = model.isSelected();
      }
    });
    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = 0;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    panel.add(commentsCheckBox, constraints);

    constraints.gridx = 0;
    constraints.gridy = 1;
    panel.add(testCaseCheckBox, constraints);
    return panel;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new EmptyCatchBlockVisitor();
  }

  private class EmptyCatchBlockVisitor extends StatementInspectionVisitor {


    public void visitTryStatement(@NotNull PsiTryStatement statement) {
      super.visitTryStatement(statement);

      if (statement.getContainingFile() instanceof JspFile) {
        return;
      }
      if (m_ignoreTestCases) {
        final PsiClass aClass =
          ClassUtils.getContainingClass(statement);
        if (aClass != null &&
            ClassUtils.isSubclass(aClass, "junit.framework.TestCase")) {
          return;
        }
      }

      final PsiCatchSection[] catchSections = statement.getCatchSections();
      for (final PsiCatchSection section : catchSections) {
        final PsiCodeBlock block = section.getCatchBlock();
        if (block != null && catchBlockIsEmpty(block)) {
          final PsiElement catchToken = section.getFirstChild();
          registerError(catchToken);
        }
      }
    }

    private boolean catchBlockIsEmpty(PsiCodeBlock block) {
      if (m_includeComments) {
        final PsiElement[] children = block.getChildren();
        for (final PsiElement child : children) {
          if (child instanceof PsiComment ||
              child instanceof PsiStatement) {
            return false;
          }
        }
        return true;
      }
      else {
        final PsiStatement[] statements = block.getStatements();
        return statements.length == 0;
      }
    }
  }
}
