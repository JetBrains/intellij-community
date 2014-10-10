/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.siyeh.ig.initialization;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class DoubleBraceInitializationInspection extends BaseInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("double.brace.initialization.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("double.brace.initialization.display.name");
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    final PsiElement parent = PsiTreeUtil.skipParentsOfType(aClass, PsiNewExpression.class, ParenthesesUtils.class);
    if (!(parent instanceof PsiVariable) && !(parent instanceof PsiAssignmentExpression)) {
      return null;
    }
    return new DoubleBraceInitializationFix();
  }

  private static class DoubleBraceInitializationFix extends InspectionGadgetsFix {

    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("double.brace.initialization.quickfix");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement().getParent();
      if (!(element instanceof PsiAnonymousClass)) {
        return;
      }
      final PsiAnonymousClass aClass = (PsiAnonymousClass)element;
      final PsiElement parent = aClass.getParent();
      if (!(parent instanceof PsiNewExpression)) {
        return;
      }
      final PsiNewExpression newExpression = (PsiNewExpression)parent;
      final PsiElement ancestor = PsiTreeUtil.skipParentsOfType(newExpression, ParenthesesUtils.class);
      final String qualifierText;
      if (ancestor instanceof PsiVariable) {
        qualifierText = ((PsiVariable)ancestor).getName();
      }
      else if (ancestor instanceof PsiAssignmentExpression) {
        final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)ancestor;
        final PsiExpression lhs = ParenthesesUtils.stripParentheses(assignmentExpression.getLExpression());
        if (!(lhs instanceof PsiReferenceExpression)) {
          return;
        }
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)lhs;
        final PsiElement target = referenceExpression.resolve();
        if (!(target instanceof PsiVariable)) {
          return;
        }
        qualifierText = referenceExpression.getText();
      }
      else {
        return;
      }
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final PsiJavaCodeReferenceElement baseClassReference = aClass.getBaseClassReference();
      final PsiElement baseClassTarget = baseClassReference.resolve();
      if (!(baseClassTarget instanceof PsiClass)) {
        return;
      }
      final PsiExpressionList argumentList = aClass.getArgumentList();
      if (argumentList == null) {
        return;
      }
      qualifyReferences(aClass, (PsiClass) baseClassTarget, qualifierText);
      final PsiClassInitializer initializer = aClass.getInitializers()[0];
      final PsiCodeBlock body = initializer.getBody();
      PsiElement child = body.getLastBodyElement();
      final PsiElement stop = body.getFirstBodyElement();
      final PsiElement anchor = PsiTreeUtil.getParentOfType(aClass, PsiMember.class, PsiStatement.class);
      if (anchor == null) {
        return;
      }
      if (anchor instanceof PsiMember) {
        final PsiMember member = (PsiMember)anchor;
        final PsiClassInitializer newInitializer = factory.createClassInitializer();
        if (member.hasModifierProperty(PsiModifier.STATIC)) {
          final PsiModifierList modifierList = newInitializer.getModifierList();
          if (modifierList != null) {
            modifierList.setModifierProperty(PsiModifier.STATIC, true);
          }
        }
        final PsiCodeBlock initializerBody = newInitializer.getBody();
        while (child != null && !child.equals(stop)) {
          initializerBody.add(child);
          child = child.getPrevSibling();
        }
        member.getParent().addAfter(newInitializer, member);
      }
      else {
        final PsiElement container = anchor.getParent();
        while (child != null && !child.equals(stop)) {
          container.addAfter(child, anchor);
          child = child.getPrevSibling();
        }
      }
      final PsiExpression newNewExpression =
        factory.createExpressionFromText("new " + baseClassReference.getText() + argumentList.getText(), aClass);
      newExpression.replace(newNewExpression);
    }

    private static void qualifyReferences(PsiElement element, final PsiClass target, final String qualifierText) {
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());
      element.accept(new JavaRecursiveElementVisitor() {
        @Override
        public void visitReferenceExpression(PsiReferenceExpression expression) {
          super.visitReferenceExpression(expression);
          if (expression.getQualifierExpression() != null) {
            return;
          }
          final PsiElement expressionTarget = expression.resolve();
          if (!(expressionTarget instanceof PsiMember)) {
            return;
          }
          final PsiMember member = (PsiMember)expressionTarget;
          final PsiClass containingClass = member.getContainingClass();
          if (!InheritanceUtil.isInheritorOrSelf(target, containingClass, true)) {
            return;
          }
          final PsiExpression newExpression = factory.createExpressionFromText(qualifierText + '.' + expression.getText(), expression);
          expression.replace(newExpression);
        }
      });
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new DoubleBraceInitializationVisitor();
  }

  private static class DoubleBraceInitializationVisitor extends BaseInspectionVisitor {

    @Override
    public void visitAnonymousClass(PsiAnonymousClass aClass) {
      super.visitAnonymousClass(aClass);
      final PsiClassInitializer[] initializers = aClass.getInitializers();
      if (initializers.length != 1) {
        return;
      }
      final PsiClassInitializer initializer = initializers[0];
      if (initializer.hasModifierProperty(PsiModifier.STATIC)) {
        // don't warn on broken code
        return;
      }
      final PsiField[] fields = aClass.getFields();
      if (fields.length != 0) {
        return;
      }
      final PsiMethod[] methods = aClass.getMethods();
      if (methods.length != 0) {
        return;
      }
      final PsiClass[] innerClasses = aClass.getInnerClasses();
      if (innerClasses.length != 0) {
        return;
      }
      final PsiJavaCodeReferenceElement reference = aClass.getBaseClassReference();
      if (reference.resolve() == null) {
        return;
      }
      registerClassError(aClass, aClass);
    }
  }
}
