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
package com.siyeh.ig.naming;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.RegExFormatter;
import com.siyeh.ig.RegExInputVerifier;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.ui.FormattedTextFieldMacFix;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import javax.swing.text.InternationalFormatter;
import java.awt.*;
import java.text.NumberFormat;
import java.util.regex.Pattern;

public class LocalVariableNamingConventionInspection extends ConventionInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreForLoopParameters = false;
  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreCatchParameters = false;
  private static final int DEFAULT_MIN_LENGTH = 1;
  private static final int DEFAULT_MAX_LENGTH = 20;
  private final RenameFix fix = new RenameFix();

  public String getGroupDisplayName() {
    return GroupNames.NAMING_CONVENTIONS_GROUP_NAME;
  }

  public String buildErrorString(PsiElement location) {
    final PsiVariable var = (PsiVariable)location.getParent();
    assert var != null;
    final String varName = var.getName();
    if (varName.length() < getMinLength()) {
      return InspectionGadgetsBundle.message("local.variable.naming.convention.problem.descriptor.short");
    }
    else if (varName.length() > getMaxLength()) {
      return InspectionGadgetsBundle.message("local.variable.naming.convention.problem.descriptor.long");
    }
    else {
      return InspectionGadgetsBundle.message("local.variable.naming.convention.problem.descriptor.regex.mismatch", getRegex());
    }
  }

  protected InspectionGadgetsFix buildFix(PsiElement location) {
    return fix;
  }

  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  protected String getDefaultRegex() {
    return "[a-z][A-Za-z]*";
  }

  protected int getDefaultMinLength() {
    return DEFAULT_MIN_LENGTH;
  }

  protected int getDefaultMaxLength() {
    return DEFAULT_MAX_LENGTH;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new NamingConventionsVisitor();
  }

  public ProblemDescriptor[] doCheckMethod(PsiMethod method, InspectionManager manager, boolean isOnTheFly) {
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) {
      return super.doCheckMethod(method, manager, isOnTheFly);
    }
    if (!containingClass.isPhysical()) {
      return super.doCheckMethod(method, manager, isOnTheFly);
    }
    final BaseInspectionVisitor visitor = createVisitor(manager, isOnTheFly);
    method.accept(visitor);
    return visitor.getErrors();
  }

  private class NamingConventionsVisitor extends BaseInspectionVisitor {


    public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
      super.visitLocalVariable(variable);
      if (m_ignoreForLoopParameters) {
        final PsiElement declStatement = variable.getParent();
        if (declStatement != null && declStatement.getParent() instanceof PsiForStatement) {
          final PsiForStatement forLoop = (PsiForStatement)declStatement.getParent();
          assert forLoop != null;
          final PsiStatement initialization = forLoop.getInitialization();
          if (declStatement.equals(initialization)) {
            return;
          }
        }
      }
      final String name = variable.getName();
      if (name == null) {
        return;
      }
      if (isValid(name)) {
        return;
      }
      registerVariableError(variable);
    }

    public void visitParameter(@NotNull PsiParameter variable) {
      final boolean isCatchParameter =
        variable.getDeclarationScope() instanceof PsiCatchSection;
      if (!isCatchParameter) {
        return;
      }
      if (m_ignoreCatchParameters && isCatchParameter) {
        return;
      }
      final String name = variable.getName();
      if (name == null) {
        return;
      }
      if (isValid(name)) {
        return;
      }
      registerVariableError(variable);
    }

  }

  private static final int LOCAL_REGEX_COLUMN_COUNT = 25;

  public JComponent createOptionsPanel() {
    final GridBagLayout layout = new GridBagLayout();
    final JPanel panel = new JPanel(layout);

    final JLabel patternLabel = new JLabel(InspectionGadgetsBundle.message("convention.pattern.option"));
    patternLabel.setHorizontalAlignment(SwingConstants.TRAILING);
    final JLabel minLengthLabel = new JLabel(InspectionGadgetsBundle.message("convention.min.length.option"));
    minLengthLabel.setHorizontalAlignment(SwingConstants.TRAILING);
    final JLabel maxLengthLabel = new JLabel(InspectionGadgetsBundle.message("convention.max.length.option"));
    maxLengthLabel.setHorizontalAlignment(SwingConstants.TRAILING);

    final JCheckBox forLoopCheckBox = new JCheckBox(InspectionGadgetsBundle.message("local.variable.naming.convention.ignore.option"), m_ignoreForLoopParameters);
    final ButtonModel forLoopModel = forLoopCheckBox.getModel();
    forLoopModel.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        m_ignoreForLoopParameters = forLoopModel.isSelected();
      }
    });
    final JCheckBox catchCheckBox = new JCheckBox(InspectionGadgetsBundle.message("local.variable.naming.convention.ignore.catch.option"), m_ignoreCatchParameters);
    final ButtonModel catchBlockModel = catchCheckBox.getModel();
    catchBlockModel.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        m_ignoreCatchParameters = catchBlockModel.isSelected();
      }
    });

    final NumberFormat format = NumberFormat.getIntegerInstance();
    format.setParseIntegerOnly(true);
    format.setMinimumIntegerDigits(1);
    format.setMaximumIntegerDigits(2);
    final InternationalFormatter formatter = new InternationalFormatter(format);
    formatter.setAllowsInvalid(false);
    formatter.setCommitsOnValidEdit(true);

    final JFormattedTextField minLengthField = new JFormattedTextField(formatter);
    final Font panelFont = panel.getFont();
    minLengthField.setFont(panelFont);
    minLengthField.setValue(m_minLength);
    minLengthField.setColumns(2);
    FormattedTextFieldMacFix.apply(minLengthField);

    final JFormattedTextField maxLengthField = new JFormattedTextField(formatter);
    maxLengthField.setFont(panelFont);
    maxLengthField.setValue(m_maxLength);
    maxLengthField.setColumns(2);
    FormattedTextFieldMacFix.apply(maxLengthField);

    final JFormattedTextField regexField = new JFormattedTextField(new RegExFormatter());
    regexField.setFont(panelFont);
    regexField.setValue(m_regexPattern);
    regexField.setColumns(LOCAL_REGEX_COLUMN_COUNT);
    regexField.setInputVerifier(new RegExInputVerifier());
    regexField.setFocusLostBehavior(JFormattedTextField.COMMIT);
    FormattedTextFieldMacFix.apply(regexField);

    final DocumentListener listener = new DocumentListener() {
      public void changedUpdate(DocumentEvent e) {
        textChanged();
      }

      public void insertUpdate(DocumentEvent e) {
        textChanged();
      }

      public void removeUpdate(DocumentEvent e) {
        textChanged();
      }

      private void textChanged() {
        m_regexPattern = (Pattern)regexField.getValue();
        m_regex = m_regexPattern.pattern();
        m_minLength = ((Number)minLengthField.getValue()).intValue();
        m_maxLength = ((Number)maxLengthField.getValue()).intValue();
      }
    };
    final Document regexDocument = regexField.getDocument();
    regexDocument.addDocumentListener(listener);
    final Document minLengthDocument = minLengthField.getDocument();
    minLengthDocument.addDocumentListener(listener);
    final Document maxLengthDocument = maxLengthField.getDocument();
    maxLengthDocument.addDocumentListener(listener);

    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = 0;
    constraints.weightx = 1.0;
    constraints.anchor = GridBagConstraints.EAST;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    panel.add(patternLabel, constraints);

    constraints.gridx = 1;
    constraints.gridy = 0;
    constraints.gridwidth = 3;
    constraints.anchor = GridBagConstraints.WEST;
    panel.add(regexField, constraints);

    constraints.gridx = 0;
    constraints.gridy = 1;
    constraints.gridwidth = 1;
    constraints.anchor = GridBagConstraints.EAST;
    panel.add(minLengthLabel, constraints);

    constraints.gridx = 1;
    constraints.gridy = 1;
    constraints.anchor = GridBagConstraints.WEST;
    panel.add(minLengthField, constraints);

    constraints.gridx = 2;
    constraints.gridy = 1;
    constraints.anchor = GridBagConstraints.EAST;
    panel.add(maxLengthLabel, constraints);

    constraints.gridx = 3;
    constraints.gridy = 1;
    constraints.anchor = GridBagConstraints.WEST;
    panel.add(maxLengthField, constraints);

    constraints.gridx = 1;
    constraints.gridy = 2;
    constraints.gridwidth = 3;
    constraints.anchor = GridBagConstraints.WEST;
    panel.add(forLoopCheckBox, constraints);

    constraints.gridx = 1;
    constraints.gridy = 3;
    constraints.gridwidth = 3;
    constraints.anchor = GridBagConstraints.WEST;
    panel.add(catchCheckBox, constraints);

    return panel;
  }
}
