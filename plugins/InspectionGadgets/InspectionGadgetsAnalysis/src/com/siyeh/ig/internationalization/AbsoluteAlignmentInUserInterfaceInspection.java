/*
 * Copyright 2011-2018 Bas Leijdekkers
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
package com.siyeh.ig.internationalization;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class AbsoluteAlignmentInUserInterfaceInspection extends BaseInspection {

  private static final Map<String, String> gridbagConstants = new HashMap<>();
  static {
    gridbagConstants.put("NORTHWEST", "FIRST_LINE_START");
    gridbagConstants.put("NORTHEAST", "FIRST_LINE_END");
    gridbagConstants.put("SOUTHWEST", "LAST_LINE_START");
    gridbagConstants.put("SOUTHEAST", "LAST_LINE_END");
  }
  private static final Map<String, String> borderLayoutConstants = new HashMap<>();
  static {
    borderLayoutConstants.put("NORTH", "PAGE_START");
    borderLayoutConstants.put("SOUTH", "PAGE_END");
    borderLayoutConstants.put("EAST", "LINE_END");
    borderLayoutConstants.put("WEST", "LINE_START");
  }
  private static final Map<String, String> flowLayoutConstants = new HashMap<>();
  static {
    flowLayoutConstants.put("LEFT", "LEADING");
    flowLayoutConstants.put("RIGHT", "TRAILING");
  }
  private static final Map<String, String> scrollPaneConstants = new HashMap<>();
  static {
    scrollPaneConstants.put("LOWER_LEFT_CORNER", "LOWER_LEADING_CORNER");
    scrollPaneConstants.put("LOWER_RIGHT_CORNER", "LOWER_TRAILING_CORNER");
    scrollPaneConstants.put("UPPER_LEFT_CORNER", "UPPER_LEADING_CORNER");
    scrollPaneConstants.put("UPPER_RIGHT_CORNER", "UPPER_TRAILING_CORNER");
  }
  private static final Map<String, String> boxLayoutConstants = new HashMap<>();
  static {
    boxLayoutConstants.put("X_AXIS", "LINE_AXIS");
    boxLayoutConstants.put("Y_AXIS", "PAGE_AXIS");
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("absolute.alignment.in.user.interface.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final String className = (String)infos[0];
    final String shortClassName = className.substring(className.lastIndexOf('.') + 1);
    return InspectionGadgetsBundle.message("absolute.alignment.in.user.interface.problem.descriptor", shortClassName);
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new AbsoluteAlignmentInUserInterfaceFix((String)infos[0], (String)infos[1]);
  }

  private static class AbsoluteAlignmentInUserInterfaceFix extends InspectionGadgetsFix {

    private final String myClassName;
    private final String myReplacement;

    public AbsoluteAlignmentInUserInterfaceFix(String className, String replacement) {
      myClassName = className;
      myReplacement = replacement;
    }

    @NotNull
    @Override
    public String getName() {
      final String shortClassName = myClassName.substring(myClassName.lastIndexOf('.') + 1);
      return InspectionGadgetsBundle.message("absolute.alignment.in.user.interface.quickfix", shortClassName, myReplacement);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with constant";
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)parent;
      PsiReplacementUtil.replaceExpression(referenceExpression, myClassName + '.' + myReplacement);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AbsoluteAlignmentInUserInterfaceVisitor();
  }

  private static class AbsoluteAlignmentInUserInterfaceVisitor extends BaseInspectionVisitor {

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      final PsiElement referenceNameElement = expression.getReferenceNameElement();
      if (referenceNameElement ==  null) {
        return;
      }
      final String referenceName = expression.getReferenceName();
      final String className;
      String value;
      if ((value = gridbagConstants.get(referenceName)) != null) {
        className = checkExpression(expression, "java.awt.GridBagConstraints");
      } else if ((value = borderLayoutConstants.get(referenceName)) != null) {
        className = checkExpression(expression, "java.awt.BorderLayout", "java.awt.GridBagConstraints");
      } else if ((value = flowLayoutConstants.get(referenceName)) != null) {
        className = checkExpression(expression, "java.awt.FlowLayout");
      } else if ((value = scrollPaneConstants.get(referenceName)) != null) {
        className = checkExpression(expression, "javax.swing.ScrollPaneConstants");
      } else if ((value = boxLayoutConstants.get(referenceName)) != null) {
        className = checkExpression(expression, "javax.swing.BoxLayout");
      } else {
        return;
      }
      if (className == null) {
        return;
      }
      registerError(referenceNameElement, className, value);
    }

    private static String checkExpression(PsiReferenceExpression expression, String... classNames) {
      final PsiElement target = expression.resolve();
      if (!(target instanceof PsiField)) {
        return null;
      }
      final PsiField field = (PsiField)target;
      final PsiClass containingClass = field.getContainingClass();
      for (String className : classNames) {
        if (InheritanceUtil.isInheritor(containingClass, className)) {
          return className;
        }
      }
      return null;
    }
  }
}
