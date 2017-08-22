/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.TextRange;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class BaseInspectionVisitor extends JavaElementVisitor {

  private BaseInspection inspection = null;
  private boolean onTheFly = false;
  private ProblemsHolder holder = null;

  final void setInspection(BaseInspection inspection) {
    this.inspection = inspection;
  }

  final void setOnTheFly(boolean onTheFly) {
    this.onTheFly = onTheFly;
  }

  public final boolean isOnTheFly() {
    return onTheFly;
  }

  protected final void registerNewExpressionError(@NotNull PsiNewExpression expression, Object... infos) {
    final PsiJavaCodeReferenceElement classReference = expression.getClassOrAnonymousClassReference();
    if (classReference == null) {
      return;
    }
    registerError(classReference, infos);
  }

  protected final void registerMethodCallError(@NotNull PsiMethodCallExpression expression, @NonNls Object... infos) {
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final PsiElement nameToken = methodExpression.getReferenceNameElement();
    if (nameToken == null) {
      return;
    }
    registerError(nameToken, infos);
  }

  protected final void registerStatementError(@NotNull PsiStatement statement, Object... infos) {
    final PsiElement statementToken = statement.getFirstChild();
    if (statementToken == null || statementToken.getText().length() == 0) {
      registerError(statement, infos);
    }
    else {
      registerError(statementToken, infos);
    }
  }

  protected final void registerModuleError(@NotNull PsiJavaModule module, Object... infos) {
    registerError(module.getNameIdentifier(), infos);
  }

  protected final void registerClassError(@NotNull PsiClass aClass, Object... infos) {
    final PsiElement nameIdentifier;
    if (aClass instanceof PsiEnumConstantInitializer) {
      final PsiEnumConstantInitializer enumConstantInitializer = (PsiEnumConstantInitializer)aClass;
      final PsiEnumConstant enumConstant = enumConstantInitializer.getEnumConstant();
      nameIdentifier = enumConstant.getNameIdentifier();
    }
    else if (aClass instanceof PsiAnonymousClass) {
      final PsiAnonymousClass anonymousClass = (PsiAnonymousClass)aClass;
      nameIdentifier = anonymousClass.getBaseClassReference();
    }
    else {
      nameIdentifier = aClass.getNameIdentifier();
    }
    if (nameIdentifier == null) {
      return;
    }
    registerError(nameIdentifier, infos);
  }

  protected final void registerMethodError(@NotNull PsiMethod method, Object... infos) {
    final PsiElement nameIdentifier = method.getNameIdentifier();
    if (nameIdentifier == null) {
      return;
    }
    registerError(nameIdentifier, infos);
  }

  protected final void registerVariableError(@NotNull PsiVariable variable, Object... infos) {
    final PsiElement nameIdentifier = variable.getNameIdentifier();
    if (nameIdentifier == null) {
      return;
    }
    registerError(nameIdentifier, infos);
  }

  protected final void registerTypeParameterError(@NotNull PsiTypeParameter typeParameter, Object... infos) {
    final PsiElement nameIdentifier = typeParameter.getNameIdentifier();
    if (nameIdentifier == null) {
      return;
    }
    registerError(nameIdentifier, infos);
  }

  protected final void registerFieldError(@NotNull PsiField field, Object... infos) {
    registerError(field.getNameIdentifier(), infos);
  }

  protected final void registerModifierError(@NotNull String modifier, @NotNull PsiModifierListOwner parameter, Object... infos) {
    final PsiModifierList modifiers = parameter.getModifierList();
    if (modifiers == null) {
      return;
    }
    final PsiElement[] children = modifiers.getChildren();
    for (final PsiElement child : children) {
      final String text = child.getText();
      if (modifier.equals(text)) {
        registerError(child, infos);
      }
    }
  }

  protected final void registerClassInitializerError(@NotNull PsiClassInitializer initializer, Object... infos) {
    final PsiCodeBlock body = initializer.getBody();
    final PsiJavaToken lBrace = body.getLBrace();
    if (lBrace == null) {
      registerError(initializer, infos);
    }
    else {
      registerError(lBrace, infos);
    }
  }

  protected final void registerError(@NotNull PsiElement location, Object... infos) {
    registerError(location, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, infos);
  }

  protected final void registerError(@NotNull PsiElement location,
                                     final ProblemHighlightType highlightType,
                                     Object... infos) {
    assert location.getTextLength() != 0 || location instanceof PsiFile;
    final LocalQuickFix[] fixes = createAndInitFixes(infos);
    final String description = inspection.buildErrorString(infos);
    holder.registerProblem(location, description, highlightType, fixes);
  }

  protected final void registerErrorAtOffset(@NotNull PsiElement location, int offset, int length, Object... infos) {
    registerErrorAtOffset(location, offset, length, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, infos);
  }

  protected final void registerErrorAtOffset(@NotNull PsiElement location, int offset, int length,
                                             ProblemHighlightType highlightType,
                                             Object... infos) {
    assert !(location.getTextLength() == 0 || length == 0);
    final LocalQuickFix[] fixes = createAndInitFixes(infos);
    final String description = inspection.buildErrorString(infos);
    final TextRange range = new TextRange(offset, offset + length);
    holder.registerProblem(location, description, highlightType, range, fixes);
  }

  protected final void registerErrorAtRange(@NotNull PsiElement startLocation, @NotNull PsiElement endLocation, Object... infos) {
    if (startLocation.getTextLength() == 0 && startLocation == endLocation) {
      return;
    }
    final LocalQuickFix[] fixes = createAndInitFixes(infos);
    final String description = inspection.buildErrorString(infos);
    final ProblemDescriptor problemDescriptor = holder.getManager()
      .createProblemDescriptor(startLocation, endLocation, description, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, onTheFly, fixes);
    holder.registerProblem(problemDescriptor);
  }

  @NotNull
  private LocalQuickFix[] createAndInitFixes(Object[] infos) {
    final InspectionGadgetsFix[] fixes = createFixes(infos);
    for (InspectionGadgetsFix fix : fixes) {
      fix.setOnTheFly(onTheFly);
    }
    return fixes;
  }

  @NotNull
  private InspectionGadgetsFix[] createFixes(Object... infos) {
    if (!onTheFly && inspection.buildQuickFixesOnlyForOnTheFlyErrors()) {
      return InspectionGadgetsFix.EMPTY_ARRAY;
    }
    final InspectionGadgetsFix[] fixes = inspection.buildFixes(infos);
    if (fixes.length > 0) {
      return fixes;
    }
    final InspectionGadgetsFix fix = inspection.buildFix(infos);
    if (fix == null) {
      return InspectionGadgetsFix.EMPTY_ARRAY;
    }
    return new InspectionGadgetsFix[]{fix};
  }

  @Override
  public void visitReferenceExpression(PsiReferenceExpression expression) {
    visitExpression(expression);
  }

  public final void setProblemsHolder(ProblemsHolder holder) {
    this.holder = holder;
  }

  protected boolean isVisibleHighlight(@NotNull PsiElement element) {
    return !isOnTheFly() || !InspectionProjectProfileManager.isInformationLevel(inspection.getShortName(), element);
  }
}