// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.confusing;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyQuickFixFactory;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyCodeStyleSettingsFacade;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

/**
 * @author Max Medvedev
 */
public final class UnnecessaryQualifiedReferenceInspection extends BaseInspection {

  @Override
  protected @NotNull BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitCodeReferenceElement(@NotNull GrCodeReferenceElement refElement) {
        super.visitCodeReferenceElement(refElement);

        if (canBeSimplified(refElement)) {
          registerError(refElement);
        }
      }

      @Override
      public void visitReferenceExpression(@NotNull GrReferenceExpression referenceExpression) {
        super.visitReferenceExpression(referenceExpression);

        if (canBeSimplified(referenceExpression) || isQualifiedStaticMethodWithUnnecessaryQualifier(referenceExpression)) {
          registerError(referenceExpression);
        }
      }
    };
  }

  private static boolean isQualifiedStaticMethodWithUnnecessaryQualifier(GrReferenceExpression ref) {
    if (ref.getQualifier() == null) return false;
    if (ref.hasAt()) return false;

    final PsiElement resolved = ref.resolve();
    if (!(resolved instanceof PsiMember)) return false;
    if (!((PsiMember)resolved).hasModifierProperty(PsiModifier.STATIC)) return false;
    if (GroovyConfigUtils.isAtLeastGroovy40(ref)) {
      PsiClass container = ((PsiMember)resolved).getContainingClass();
      if (container != null && container.isInterface()) {
        return false;
      }
    }

    PsiElement copyResolved;
    final PsiElement parent = ref.getParent();
    if (parent instanceof GrMethodCall) {
      final GrMethodCall copy = (GrMethodCall)parent.copy();
      GrReferenceExpression invoked = (GrReferenceExpression)copy.getInvokedExpression();
      assert invoked != null;

      invoked.setQualifier(null);

      copyResolved = ((GrReferenceExpression)copy.getInvokedExpression()).resolve();
    }
    else {
      final GrReferenceExpression copy = (GrReferenceExpression)ref.copy();
      copy.setQualifier(null);
      copyResolved = copy.resolve();
    }
    return ref.getManager().areElementsEquivalent(copyResolved, resolved);
  }

  @Override
  protected String buildErrorString(Object... args) {
    return GroovyBundle.message("inspection.display.name.unnecessary.qualified.reference");
  }

  @Override
  protected LocalQuickFix buildFix(@NotNull PsiElement location) {
    return GroovyQuickFixFactory.getInstance().createReplaceWithImportFix();
  }

  private static boolean canBeSimplified(PsiElement element) {
    if (PsiTreeUtil.getParentOfType(element, PsiComment.class) != null) return false;

    if (element instanceof GrCodeReferenceElement) {
      if (PsiTreeUtil.getParentOfType(element, GrImportStatement.class, GrPackageDefinition.class) != null) return false;
    }
    else if (element instanceof GrReferenceExpression) {
      if (!PsiImplUtil.seemsToBeQualifiedClassName((GrReferenceExpression)element)) return false;
    }
    else {
      return false;
    }

    final GrReferenceElement<?> ref = (GrReferenceElement<?>)element;
    if (ref.getQualifier() == null) return false;
    if (!(ref.getContainingFile() instanceof GroovyFileBase)) return false;

    final PsiElement resolved = ref.resolve();
    if (!(resolved instanceof PsiClass)) return false;

    final String name = ((PsiClass)resolved).getName();
    if (name == null) return false;

    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(element.getProject());
    final GrReferenceExpression shortedRef = factory.createReferenceExpressionFromText(name, element);
    final GroovyResolveResult resolveResult = shortedRef.advancedResolve();

    if (element.getManager().areElementsEquivalent(resolved, resolveResult.getElement())) {
      return true;
    }

    final PsiClass containingClass = ((PsiClass)resolved).getContainingClass();
    if (containingClass != null &&
        !GroovyCodeStyleSettingsFacade.getInstance(containingClass.getProject()).insertInnerClassImports()) {
      return false;
    }

    return resolveResult.getElement() == null || !resolveResult.isAccessible() || !resolveResult.isStaticsOK();
  }
}
