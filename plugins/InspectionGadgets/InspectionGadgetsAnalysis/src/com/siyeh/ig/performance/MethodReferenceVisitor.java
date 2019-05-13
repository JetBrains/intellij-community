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
package com.siyeh.ig.performance;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NotNull;

class MethodReferenceVisitor extends JavaRecursiveElementWalkingVisitor {

  private boolean m_referencesStaticallyAccessible = true;
  private final PsiMember m_method;

  MethodReferenceVisitor(PsiMember method) {
    m_method = method;
  }

  public boolean areReferencesStaticallyAccessible() {
    return m_referencesStaticallyAccessible;
  }

  @Override
  public void visitElement(PsiElement element) {
    if (!m_referencesStaticallyAccessible) {
      return;
    }
    super.visitElement(element);
  }

  @Override
  public void visitReferenceElement(
    PsiJavaCodeReferenceElement reference) {
    super.visitReferenceElement(reference);
    final PsiElement resolvedElement = reference.resolve();
    if (!(resolvedElement instanceof PsiClass)) {
      return;
    }
    final PsiClass aClass = (PsiClass)resolvedElement;
    final PsiElement scope = aClass.getScope();
    if (!(scope instanceof PsiClass)) {
      return;
    }
    if (aClass.hasModifierProperty(PsiModifier.STATIC)) {
      return;
    }
    m_referencesStaticallyAccessible = false;
  }

  @Override
  public void visitReferenceExpression(
    @NotNull PsiReferenceExpression expression) {
    super.visitReferenceExpression(expression);
    final PsiElement qualifier = expression.getQualifierExpression();
    if (qualifier != null && !(qualifier instanceof PsiThisExpression) &&
        !(qualifier instanceof PsiSuperExpression)) {
      return;
    }
    final PsiElement element = expression.resolve();
    if (element instanceof PsiMember) {
      final PsiMember member = (PsiMember)element;
      if (isMemberStaticallyAccessible(member)) {
        return;
      }
    }
    else if (element != null) {
      return;
    }
    m_referencesStaticallyAccessible = false;
  }

  @Override
  public void visitThisExpression(
    @NotNull PsiThisExpression expression) {
    super.visitThisExpression(expression);
    m_referencesStaticallyAccessible = false;
  }

  private boolean isMemberStaticallyAccessible(PsiMember member) {
    if (m_method.equals(member)) {
      return true;
    }
    if (member.hasModifierProperty(PsiModifier.STATIC)) {
      return true;
    }
    final PsiClass referenceContainingClass = m_method.getContainingClass();
    final PsiClass containingClass = member.getContainingClass();
    return !InheritanceUtil.isInheritorOrSelf(referenceContainingClass,
                                              containingClass, true);
  }
}
