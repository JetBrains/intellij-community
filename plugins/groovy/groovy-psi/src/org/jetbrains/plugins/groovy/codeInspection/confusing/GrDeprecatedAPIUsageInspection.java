// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.confusing;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.impl.PsiImplUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyReference;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

/**
 * @author Max Medvedev
 */
public final class GrDeprecatedAPIUsageInspection extends BaseInspection {

  @Override
  protected @NotNull BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitReferenceExpression(@NotNull GrReferenceExpression ref) {
        super.visitReferenceExpression(ref);
        PsiElement resolveResult = getResolveElement(ref);
        checkRef(resolveResult, ref.getReferenceNameElement(), ref.getReferenceName());
      }

      @Override
      public void visitCodeReferenceElement(@NotNull GrCodeReferenceElement ref) {
        super.visitCodeReferenceElement(ref);
        PsiElement resolveResult = getResolveElement(ref);
        checkRef(resolveResult, ref.getReferenceNameElement(), ref.getReferenceName());
      }

      @Override
      public void visitArgumentLabel(@NotNull GrArgumentLabel argumentLabel) {
        super.visitArgumentLabel(argumentLabel);
        PsiElement resolveResult = getResolveElement(argumentLabel);
        if (resolveResult instanceof GrAccessorMethod) {
          resolveResult = ((GrAccessorMethod)resolveResult).getProperty();
        }
        checkRef(resolveResult, argumentLabel.getNameElement(), argumentLabel.getName());
      }

      @Override
      public void visitNewExpression(@NotNull GrNewExpression ref) {
        super.visitNewExpression(ref);
        var resolvedCall = ref.resolveMethod();
        if (resolvedCall == null || isDeprecated(resolvedCall.getContainingClass())) {
          return;
        }
        var referenceElement = ref.getReferenceElement();
        if (referenceElement != null) {
          checkRef(resolvedCall, ref.getReferenceElement(), referenceElement.getReferenceName());
        }
        else {
          checkRef(resolvedCall, ref, resolvedCall.getName());
        }
      }

      private void checkRef(PsiElement resolved, PsiElement elementToHighlight, String elementName) {
        if (isDeprecated(resolved)) {
          registerError(elementToHighlight, GroovyBundle.message("0.is.deprecated", elementName), LocalQuickFix.EMPTY_ARRAY,
                        ProblemHighlightType.LIKE_DEPRECATED);
        }
      }

      private static @Nullable PsiElement getResolveElement(GroovyReference reference) {
        GroovyResolveResult[] results = reference.multiResolve(false);
        for (GroovyResolveResult result : results) {
          PsiElement element = result.getElement();
          if (element != null) {
            return element;
          }
        }
        return null;
      }

      private static boolean isDeprecated(PsiElement resolved) {
        if (resolved instanceof PsiDocCommentOwner) {
          return ((PsiDocCommentOwner)resolved).isDeprecated();
        }
        if (resolved instanceof PsiModifierListOwner && PsiImplUtil.isDeprecatedByAnnotation((PsiModifierListOwner)resolved)) {
          return true;
        }
        return false;
      }
    };
  }
}
