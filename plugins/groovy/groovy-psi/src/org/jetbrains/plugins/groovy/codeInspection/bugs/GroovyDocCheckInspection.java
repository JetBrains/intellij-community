// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocFieldReference;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMemberReference;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMethodReference;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GroovyDocPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

/**
 * @author Max Medvedev
 */
public final class GroovyDocCheckInspection extends BaseInspection {

  @Override
  protected String buildErrorString(Object... args) {
    return (String)args[0];
  }

  @Override
  protected @NotNull BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitDocMethodReference(@NotNull GrDocMethodReference reference) {
        checkGrDocMemberReference(reference);
      }

      @Override
      public void visitDocFieldReference(@NotNull GrDocFieldReference reference) {
        checkGrDocMemberReference(reference);
      }

      @Override
      public void visitCodeReferenceElement(@NotNull GrCodeReferenceElement refElement) {
        GroovyResolveResult resolveResult = refElement.advancedResolve();
        if (refElement.getReferenceName() == null) return;

        if (PsiTreeUtil.getParentOfType(refElement, GroovyDocPsiElement.class, true, GrMember.class, GrCodeBlock.class) == null) return;

        final PsiElement resolved = resolveResult.getElement();
        if (resolved != null) return;

        final PsiElement toHighlight = refElement.getReferenceNameElement();

        registerError(toHighlight, GroovyBundle.message("cannot.resolve", refElement.getReferenceName()));
      }

      private void checkGrDocMemberReference(final GrDocMemberReference reference) {
        if (reference.resolve() != null) return;

        registerError(reference.getReferenceNameElement(), GroovyBundle.message("cannot.resolve", reference.getReferenceName()));
      }
    };
  }
}
