/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.numeric;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ConfusingFloatingPointLiteralInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreScientificNotation = false;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("confusing.floating.point.literal.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("confusing.floating.point.literal.problem.descriptor");
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("confusing.floating.point.literal.option"), this,
                                          "ignoreScientificNotation");
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    if (ignoreScientificNotation) {
      node.addContent(new Element("option").setAttribute("name", "ignoreScientificNotation").setAttribute("value", "true"));
    }
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new ConfusingFloatingPointLiteralFix();
  }

  private static class ConfusingFloatingPointLiteralFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("confusing.floating.point.literal.change.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiExpression literalExpression = (PsiExpression)descriptor.getPsiElement();
      final String text = literalExpression.getText();
      final String newText = getCanonicalForm(text);
      PsiReplacementUtil.replaceExpression(literalExpression, newText);
    }

    private static String getCanonicalForm(@NonNls String text) {
      final boolean isHexadecimal = text.startsWith("0x") || text.startsWith("0X");
      int breakPoint = text.indexOf((int)'e');
      if (breakPoint < 0) {
        breakPoint = text.indexOf((int)'E');
      }
      if (breakPoint < 0) {
        breakPoint = text.indexOf((int)'f');
      }
      if (breakPoint < 0) {
        breakPoint = text.indexOf((int)'F');
      }
      if (breakPoint < 0) {
        breakPoint = text.indexOf((int)'p');
      }
      if (breakPoint < 0) {
        breakPoint = text.indexOf((int)'P');
      }
      if (breakPoint < 0) {
        breakPoint = text.indexOf((int)'d');
      }
      if (breakPoint < 0) {
        breakPoint = text.indexOf((int)'D');
      }
      final String suffix;
      final String prefix;
      if (breakPoint < 0) {
        suffix = "";
        prefix = text;
      }
      else {
        suffix = text.substring(breakPoint);
        prefix = text.substring(0, breakPoint);
      }
      final int indexPoint = prefix.indexOf((int)'.');
      if (indexPoint < 0) {
        return prefix + ".0" + suffix;
      }
      else if (isHexadecimal && indexPoint == 2) {
        return prefix.substring(0, 2) + '0' + prefix.substring(2) + suffix;
      }
      else if (indexPoint == 0) {
        return '0' + prefix + suffix;
      }
      else {
        return prefix + '0' + suffix;
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ConfusingFloatingPointLiteralVisitor();
  }

  private class ConfusingFloatingPointLiteralVisitor extends BaseInspectionVisitor {

    @Override
    public void visitLiteralExpression(@NotNull PsiLiteralExpression literal) {
      super.visitLiteralExpression(literal);
      final PsiType type = literal.getType();
      final String literalText = literal.getText();
      if ((!PsiType.FLOAT.equals(type) && !PsiType.DOUBLE.equals(type)) || !isConfusing(literalText)) {
        return;
      }
      if (ignoreScientificNotation && StringUtil.containsAnyChar(literalText, "EePp")) {
        return;
      }
      registerError(literal);
    }

    private boolean isConfusing(@Nullable CharSequence text) {
      if (text == null) {
        return false;
      }
      final int length = text.length();
      if (length < 3) {
        return true;
      }
      boolean hex = true;
      final char firstChar = text.charAt(0);
      if (firstChar != '0') {
        if (!StringUtil.isDecimalDigit(firstChar)) {
          return true;
        }
        hex = false;
      }
      final char secondChar = text.charAt(1);
      if (hex && secondChar != 'x' && secondChar != 'X') {
        hex = false;
      }
      int index = hex ? 2 : 1;
      char nextChar = text.charAt(index);
      if (hex) {
        if (!StringUtil.isHexDigit(nextChar)) {
          return true;
        }
      }
      while (hex && StringUtil.isHexDigit(nextChar) || StringUtil.isDecimalDigit(nextChar) || nextChar == '_') {
        index++;
        if (index >= length) {
          return true;
        }
        nextChar = text.charAt(index);
      }
      if (nextChar != '.') {
        return true;
      }
      index++;
      if (index >= length) {
        return true;
      }
      nextChar = text.charAt(index);
      if (hex) {
        if (!StringUtil.isHexDigit(nextChar)) {
          return true;
        }
      } else if (!StringUtil.isDecimalDigit(nextChar)) {
        return true;
      }
      // <digit(s)><point><digit> seen, skip the rest.
      return false;
    }
  }
}