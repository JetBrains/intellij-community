/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.memory;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class InnerClassMayBeStaticInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("inner.class.may.be.static.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("inner.class.may.be.static.problem.descriptor");
  }

  @Override
  public boolean runForWholeFile() {
    return true;
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new InnerClassMayBeStaticFix();
  }

  private static class InnerClassMayBeStaticFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("make.static.quickfix");
    }

    @Override
    protected boolean prepareForWriting() {
      return false;
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiJavaToken classNameToken = (PsiJavaToken)descriptor.getPsiElement();
      final PsiClass innerClass = (PsiClass)classNameToken.getParent();
      if (innerClass == null) {
        return;
      }
      final SearchScope useScope = innerClass.getUseScope();
      final Query<PsiReference> query = ReferencesSearch.search(innerClass, useScope);
      final Collection<PsiReference> references = query.findAll();
      final List<PsiElement> elements = new ArrayList<>(references.size() + 1);
      for (PsiReference reference : references) {
        elements.add(reference.getElement());
      }
      elements.add(innerClass);
      if (!FileModificationService.getInstance().preparePsiElementsForWrite(elements)) {
        return;
      }
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      for (final PsiReference reference : references) {
        final PsiElement element = reference.getElement();
        final PsiElement parent = element.getParent();
        if (!(parent instanceof PsiNewExpression)) {
          continue;
        }
        final PsiNewExpression newExpression = (PsiNewExpression)parent;
        final PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
        if (classReference == null) {
          continue;
        }
        final PsiExpressionList argumentList = newExpression.getArgumentList();
        if (argumentList == null) {
          continue;
        }
        final PsiReferenceParameterList parameterList = classReference.getParameterList();
        final String genericParameters = parameterList != null ? parameterList.getText() : "";
        final PsiExpression expression = factory
          .createExpressionFromText("new " + classReference.getQualifiedName() + genericParameters + argumentList.getText(), innerClass);
        codeStyleManager.shortenClassReferences(newExpression.replace(expression));
      }
      final PsiModifierList modifiers = innerClass.getModifierList();
      if (modifiers == null) {
        return;
      }
      modifiers.setModifierProperty(PsiModifier.STATIC, true);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new InnerClassMayBeStaticVisitor();
  }

  private static class InnerClassMayBeStaticVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      if (aClass.getContainingClass() != null && !aClass.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      if (aClass instanceof PsiAnonymousClass) {
        return;
      }
      final PsiClass[] innerClasses = aClass.getInnerClasses();
      for (final PsiClass innerClass : innerClasses) {
        if (innerClass.hasModifierProperty(PsiModifier.STATIC)) {
          continue;
        }
        final InnerClassReferenceVisitor visitor = new InnerClassReferenceVisitor(innerClass);
        innerClass.accept(visitor);
        if (!visitor.canInnerClassBeStatic()) {
          continue;
        }
        registerClassError(innerClass);
      }
    }
  }
}