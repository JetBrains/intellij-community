/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ig.style;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class UnqualifiedInnerClassAccessInspectionBase extends BaseInspection {
  @SuppressWarnings({"PublicField"})
  public boolean ignoreReferencesToLocalInnerClasses = false;

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("unqualified.inner.class.access.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("unqualified.inner.class.access.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnqualifiedInnerClassAccessVisitor();
  }

  protected static class ReferenceCollector extends JavaRecursiveElementVisitor {

    private final String name;
    private final boolean onDemand;
    private final Set<PsiJavaCodeReferenceElement> references = new HashSet<PsiJavaCodeReferenceElement>();

    ReferenceCollector(String name, boolean onDemand) {
      this.name = name;
      this.onDemand = onDemand;
    }

    @Override
    public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
      super.visitReferenceElement(reference);
      if (reference.isQualified()) {
        return;
      }
      final PsiElement target = reference.resolve();
      if (!(target instanceof PsiClass)) {
        return;
      }
      final PsiClass aClass = (PsiClass)target;
      if (!onDemand) {
        final String qualifiedName = aClass.getQualifiedName();
        if (name.equals(qualifiedName)) {
          references.add(reference);
        }
        return;
      }
      final PsiClass containingClass = aClass.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final String qualifiedName = containingClass.getQualifiedName();
      if (name.equals(qualifiedName)) {
        references.add(reference);
      }
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      visitReferenceElement(expression);
    }

    public Collection<PsiJavaCodeReferenceElement> getReferences() {
      return references;
    }
  }

  private class UnqualifiedInnerClassAccessVisitor extends BaseInspectionVisitor {

    @Override
    public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
      super.visitReferenceElement(reference);
      if (reference.isQualified()) {
        return;
      }
      final PsiElement target = reference.resolve();
      if (!(target instanceof PsiClass)) {
        return;
      }
      final PsiClass aClass = (PsiClass)target;
      final PsiClass containingClass = aClass.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (ignoreReferencesToLocalInnerClasses) {
        if (PsiTreeUtil.isAncestor(containingClass, reference, true)) {
          return;
        }
        final PsiClass referenceClass = PsiTreeUtil.getParentOfType(reference, PsiClass.class);
        if (referenceClass != null && referenceClass.isInheritor(containingClass, true)) {
          return;
        }
      }
      registerError(reference, containingClass.getName());
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      visitReferenceElement(expression);
    }
  }
}
