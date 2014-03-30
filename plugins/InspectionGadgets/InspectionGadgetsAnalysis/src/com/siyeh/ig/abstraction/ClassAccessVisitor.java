/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.abstraction;

import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.LibraryUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

class ClassAccessVisitor extends JavaRecursiveElementVisitor {

  private final Map<PsiClass, Integer> m_accessCounts =
    new HashMap<PsiClass, Integer>(2);
  private final Set<PsiClass> m_overAccessedClasses =
    new HashSet<PsiClass>(2);
  private final PsiClass currentClass;

  ClassAccessVisitor(PsiClass currentClass) {
    super();
    this.currentClass = currentClass;
  }

  @Override
  public void visitMethodCallExpression(
    @NotNull PsiMethodCallExpression expression) {
    super.visitMethodCallExpression(expression);
    final PsiMethod method = expression.resolveMethod();
    if (method == null) {
      return;
    }
    final PsiClass calledClass = method.getContainingClass();
    if (calledClass == null) {
      return;
    }
    if (currentClass.equals(calledClass)) {
      return;
    }
    final Set<PsiClass> overAccessedClasses = m_overAccessedClasses;
    if (overAccessedClasses.contains(calledClass)) {
      return;
    }
    if (LibraryUtil.classIsInLibrary(calledClass)) {
      return;
    }
    if (PsiTreeUtil.isAncestor(currentClass, calledClass, true)) {
      return;
    }
    if (PsiTreeUtil.isAncestor(calledClass, currentClass, true)) {
      return;
    }
    PsiClass lexicallyEnclosingClass = currentClass;
    while (lexicallyEnclosingClass != null) {
      if (lexicallyEnclosingClass.isInheritor(calledClass, true)) {
        return;
      }
      lexicallyEnclosingClass =
        ClassUtils.getContainingClass(lexicallyEnclosingClass);
    }
    final Map<PsiClass, Integer> accessCounts = m_accessCounts;
    final Integer count = accessCounts.get(calledClass);
    if (count == null) {
      accessCounts.put(calledClass, Integer.valueOf(1));
    }
    else if (count.equals(Integer.valueOf(1))) {
      accessCounts.put(calledClass, Integer.valueOf(2));
    }
    else {
      overAccessedClasses.add(calledClass);
    }
  }

  public Set<PsiClass> getOveraccessedClasses() {
    return Collections.unmodifiableSet(m_overAccessedClasses);
  }
}