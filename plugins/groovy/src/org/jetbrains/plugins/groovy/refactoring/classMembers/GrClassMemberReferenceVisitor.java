// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.classMembers;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author Max Medvedev
 */
public abstract class GrClassMemberReferenceVisitor extends GroovyRecursiveElementVisitor {
  private final PsiClass myClass;

  public GrClassMemberReferenceVisitor(@NotNull PsiClass aClass) {
    myClass = aClass;
  }

  @Override
  public void visitReferenceExpression(@NotNull GrReferenceExpression ref) {
    GrExpression qualifier = ref.getQualifier();

    if (qualifier != null && !(PsiUtil.isThisOrSuperRef(qualifier))) {
      qualifier.accept(this);

      if (!(qualifier instanceof GrReferenceExpression) || !(((GrReferenceExpression) qualifier).resolve() instanceof PsiClass)) {
        return;
      }
    }

    GroovyResolveResult resolveResult = ref.advancedResolve();
    PsiElement resolved = resolveResult.getElement();
    if (resolved instanceof GrMember) {
      PsiClass containingClass = ((GrMember)resolved).getContainingClass();
      if (isPartOf(myClass, containingClass)) {
        visitClassMemberReferenceElement(ref, (GrMember)resolved, resolveResult);
      }
    }
  }

  @Override
  public void visitCodeReferenceElement(@NotNull GrCodeReferenceElement reference) {
    GroovyResolveResult resolveResult = reference.advancedResolve();
    PsiElement referencedElement = resolveResult.getElement();
    if (referencedElement instanceof GrTypeDefinition) {
      final GrTypeDefinition referencedClass = (GrTypeDefinition)referencedElement;
      if (PsiTreeUtil.isAncestor(myClass, referencedElement, true) ||
          isPartOf(myClass, referencedClass.getContainingClass())) {
        visitClassMemberReferenceElement(reference, (GrMember)referencedElement, resolveResult);
      }
    }
  }

  private static boolean isPartOf(@NotNull PsiClass aClass, @Nullable PsiClass containingClass) {
    if (containingClass == null) return false;
    return aClass.equals(containingClass) || aClass.isInheritor(containingClass, true);
  }

  protected void visitClassMemberReferenceElement(GrReferenceElement<?> ref, GrMember member, GroovyResolveResult resolveResult) {
    visitClassMemberReferenceElement(member, ref);
  }

  protected void visitClassMemberReferenceElement(GrMember resolved, GrReferenceElement ref) {
    throw new RuntimeException("Override one of visitClassMemberReferenceElement() methods");
  }
}
