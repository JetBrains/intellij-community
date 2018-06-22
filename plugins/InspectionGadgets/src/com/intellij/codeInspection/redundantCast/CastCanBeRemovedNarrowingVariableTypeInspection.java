// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.redundantCast;

import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.RedundantCastUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.HighlightUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.ObjectUtils.tryCast;

public class CastCanBeRemovedNarrowingVariableTypeInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitTypeCastExpression(PsiTypeCastExpression cast) {
        PsiTypeElement castTypeElement = cast.getCastType();
        if (castTypeElement == null || castTypeElement.getAnnotations().length > 0) return;
        PsiType castType = cast.getType();
        if (!(castType instanceof PsiClassType) || ((PsiClassType)castType).isRaw()) return;
        PsiReferenceExpression ref = tryCast(cast.getOperand(), PsiReferenceExpression.class);
        PsiLocalVariable variable = ExpressionUtils.resolveLocalVariable(ref);
        if (variable == null) return;
        PsiTypeElement variableTypeElement = variable.getTypeElement();
        if (variableTypeElement.isInferredType() || variableTypeElement.getAnnotations().length > 0) return;
        PsiType variableType = variable.getType();
        if (!(variableType instanceof PsiClassType) || ((PsiClassType)variableType).isRaw()) return;
        if (variableType.equals(castType) || !variableType.isAssignableFrom(castType)) return;

        PsiExpression variableInitializer = variable.getInitializer();
        if (variableInitializer != null) {
          PsiType initializerType = variableInitializer.getType();
          if (initializerType == null || !castType.isAssignableFrom(initializerType)) return;
        }
        PsiElement block = PsiUtil.getVariableCodeBlock(variable, null);
        if (block == null) return;
        boolean redundantCast = PsiTreeUtil.processElements(block, e -> {
          if (e instanceof PsiReferenceExpression) {
            PsiReferenceExpression reference = (PsiReferenceExpression)e;
            return !reference.isReferenceTo(variable) || isVariableTypeChangeSafeForReference(cast, castType, reference);
          }
          return true;
        });
        if (redundantCast) {
          String message = InspectionsBundle
            .message("inspection.cast.can.be.removed.narrowing.variable.type.message", variable.getName(), castType.getPresentableText());
          holder.registerProblem(castTypeElement, message, new CastCanBeRemovedNarrowingVariableTypeFix(variable, castType, isOnTheFly));
        }
      }
    };
  }

  private static boolean isVariableTypeChangeSafeForReference(@NotNull PsiTypeCastExpression cast,
                                                              @NotNull PsiType targetType,
                                                              @NotNull PsiReferenceExpression reference) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(reference.getParent());
    if (PsiUtil.isAccessedForWriting(reference)) {
      PsiAssignmentExpression assignmentExpression = tryCast(parent, PsiAssignmentExpression.class);
      if (assignmentExpression == null) return false;
      PsiExpression rValue = assignmentExpression.getRExpression();
      if (rValue == null) return false;
      PsiType rValueType = rValue.getType();
      return rValueType != null && targetType.isAssignableFrom(rValueType);
    }
    while (parent instanceof PsiConditionalExpression) {
      parent = PsiUtil.skipParenthesizedExprUp(parent.getParent());
    }
    if (parent instanceof PsiInstanceOfExpression) {
      PsiTypeElement checkTypeElement = ((PsiInstanceOfExpression)parent).getCheckType();
      if (checkTypeElement == null) return false;
      PsiType checkType = checkTypeElement.getType();
      // Could be always false instanceof which will become compilation error after fix
      return TypeConversionUtil.areTypesConvertible(targetType, checkType);
    }
    if (parent instanceof PsiTypeCastExpression && parent != cast) {
      PsiTypeElement castTypeElement = ((PsiTypeCastExpression)parent).getCastType();
      if (castTypeElement == null) return false;
      PsiType castType = castTypeElement.getType();
      // Another cast could become invalid due to this change
      return TypeConversionUtil.areTypesConvertible(targetType, castType);
    }
    // Some method call can be mis-resolved after update, check this
    if (parent instanceof PsiExpressionList && parent.getParent() instanceof PsiCallExpression) {
      PsiCallExpression call = (PsiCallExpression)parent.getParent();
      PsiMethod method = call.resolveMethod();
      if (method == null) return false;
      Object mark = new Object();
      PsiTreeUtil.mark(reference, mark);
      PsiCallExpression callCopy = (PsiCallExpression)call.copy();
      PsiTreeUtil.releaseMark(reference, mark);
      PsiElement refCopy = PsiTreeUtil.releaseMark(callCopy, mark);
      if (refCopy == null) return false;
      refCopy.replace(cast);
      return callCopy.resolveMethod() == method;
    }
    return true;
  }

  private static class CastCanBeRemovedNarrowingVariableTypeFix implements LocalQuickFix {
    private final String myVariableName;
    private final String myType;
    private final boolean myOnTheFly;

    public CastCanBeRemovedNarrowingVariableTypeFix(PsiLocalVariable variable, PsiType type, boolean onTheFly) {
      myVariableName = variable.getName();
      myType = type.getPresentableText();
      myOnTheFly = onTheFly;
    }

    @NotNull
    @Override
    public String getName() {
      return InspectionsBundle.message("inspection.cast.can.be.removed.narrowing.variable.type.fix.name", myVariableName, myType);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.cast.can.be.removed.narrowing.variable.type.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiTypeCastExpression cast = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiTypeCastExpression.class);
      if (cast == null) return;
      PsiLocalVariable var = ExpressionUtils.resolveLocalVariable(cast.getOperand());
      if (var == null) return;
      PsiTypeElement castType = cast.getCastType();
      if (castType == null) return;
      PsiElement newTypeElement = JavaCodeStyleManager.getInstance(project).shortenClassReferences(var.getTypeElement().replace(castType));
      if (myOnTheFly) {
        HighlightUtils.highlightElement(newTypeElement);
      }
      for (PsiReference reference : ReferencesSearch.search(var).findAll()) {
        if (reference instanceof PsiReferenceExpression) {
          PsiTypeCastExpression castOccurrence =
            tryCast(PsiUtil.skipParenthesizedExprUp(((PsiReferenceExpression)reference).getParent()), PsiTypeCastExpression.class);
          if (castOccurrence != null && RedundantCastUtil.isCastRedundant(castOccurrence)) {
            RedundantCastUtil.removeCast(castOccurrence);
          }
        }
      }
    }
  }
}
