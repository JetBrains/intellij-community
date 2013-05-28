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
package com.siyeh.ig.threading;

import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

class VariableAccessVisitor extends JavaRecursiveElementVisitor {

  private final PsiClass aClass;
  private final Set<PsiField> m_synchronizedAccesses =
    new HashSet<PsiField>(2);
  private final Set<PsiField> m_unsynchronizedAccesses =
    new HashSet<PsiField>(2);
  private final Set<PsiMethod> methodsAlwaysSynchronized =
    new HashSet<PsiMethod>();
  private final Set<PsiMethod> methodsNotAlwaysSynchronized =
    new HashSet<PsiMethod>();
  private final Set<PsiMethod> unusedMethods = new HashSet<PsiMethod>();
  private final Set<PsiMethod> usedMethods = new HashSet<PsiMethod>();
  private boolean m_inInitializer = false;
  private boolean m_inSynchronizedContext = false;
  private boolean privateMethodUsagesCalculated = false;
  private final boolean countGettersAndSetters;

  VariableAccessVisitor(PsiClass aClass, boolean countGettersAndSetters) {
    super();
    this.aClass = aClass;
    this.countGettersAndSetters = countGettersAndSetters;
  }

  @Override
  public void visitClass(PsiClass classToVisit) {
    calculatePrivateMethodUsagesIfNecessary();
    super.visitClass(classToVisit);
  }

  @Override
  public void visitReferenceExpression(@NotNull PsiReferenceExpression ref) {
    super.visitReferenceExpression(ref);
    final PsiExpression qualifier = ref.getQualifierExpression();
    if (qualifier != null && !(qualifier instanceof PsiThisExpression)) {
      return;
    }
    final PsiElement element = ref.resolve();
    if (!(element instanceof PsiField)) {
      return;
    }
    if (m_inInitializer) {
    }
    else if (m_inSynchronizedContext) {
      m_synchronizedAccesses.add((PsiField)element);
    }
    else if (ref.getParent() instanceof PsiSynchronizedStatement) {
      //covers the very specific case of a field reference being directly
      // used as a lock
      m_synchronizedAccesses.add((PsiField)element);
    }
    else {
      m_unsynchronizedAccesses.add((PsiField)element);
    }
  }

  @Override
  public void visitMethodCallExpression(PsiMethodCallExpression expression) {
    super.visitMethodCallExpression(expression);
    if (!countGettersAndSetters) {
      return;
    }
    final PsiReferenceExpression methodExpression =
      expression.getMethodExpression();
    final PsiExpression qualifier =
      methodExpression.getQualifierExpression();
    if (qualifier != null && !(qualifier instanceof PsiThisExpression)) {
      return;
    }
    final PsiMethod method = (PsiMethod)methodExpression.resolve();
    PsiField field = PropertyUtil.getFieldOfGetter(method);
    if (field == null) {
      field = PropertyUtil.getFieldOfSetter(method);
    }
    if (field == null) {
      return;
    }
    if (m_inInitializer) {
    }
    else if (m_inSynchronizedContext) {
      m_synchronizedAccesses.add(field);
    }
    else {
      m_unsynchronizedAccesses.add(field);
    }
  }

  @Override
  public void visitCodeBlock(PsiCodeBlock block) {
    final boolean wasInSync = m_inSynchronizedContext;
    if (block.getParent() instanceof PsiSynchronizedStatement) {
      m_inSynchronizedContext = true;
    }
    super.visitCodeBlock(block);
    m_inSynchronizedContext = wasInSync;
  }

  @Override
  public void visitMethod(@NotNull PsiMethod method) {
    if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
      if (unusedMethods.contains(method)) {
        return;
      }
    }
    final boolean methodIsSynchonized =
      method.hasModifierProperty(PsiModifier.SYNCHRONIZED)
      || methodIsAlwaysUsedSynchronized(method);
    boolean wasInSync = false;
    if (methodIsSynchonized) {
      wasInSync = m_inSynchronizedContext;
      m_inSynchronizedContext = true;
    }
    final boolean isConstructor = method.isConstructor();
    if (isConstructor) {
      m_inInitializer = true;
    }
    super.visitMethod(method);
    if (methodIsSynchonized) {
      m_inSynchronizedContext = wasInSync;
    }
    if (isConstructor) {
      m_inInitializer = false;
    }
  }

  private boolean methodIsAlwaysUsedSynchronized(PsiMethod method) {
    if (!method.hasModifierProperty(PsiModifier.PRIVATE)) {
      return false;
    }
    return methodsAlwaysSynchronized.contains(method);
  }

  private void calculatePrivateMethodUsagesIfNecessary() {
    if (privateMethodUsagesCalculated) {
      return;
    }
    final Set<PsiMethod> privateMethods = findPrivateMethods();
    final HashMap<PsiMethod, Collection<PsiReference>> referenceMap =
      buildReferenceMap(privateMethods);
    determineUsedMethods(privateMethods, referenceMap);
    determineUsageMap(referenceMap);
    privateMethodUsagesCalculated = true;
  }

  private void determineUsageMap(HashMap<PsiMethod,
    Collection<PsiReference>> referenceMap) {
    final Set<PsiMethod> remainingMethods =
      new HashSet<PsiMethod>(usedMethods);
    boolean stabilized = false;
    while (!stabilized) {
      stabilized = true;
      final Set<PsiMethod> methodsDeterminedThisPass =
        new HashSet<PsiMethod>();
      for (PsiMethod method : remainingMethods) {
        final Collection<PsiReference> references =
          referenceMap.get(method);
        boolean areAllReferencesSynchronized = true;
        for (PsiReference reference : references) {
          if (isKnownToBeUsed(reference)) {
            if (isInKnownUnsynchronizedContext(reference)) {
              methodsNotAlwaysSynchronized.add(method);
              methodsDeterminedThisPass.add(method);
              areAllReferencesSynchronized = false;
              stabilized = false;
              break;
            }
            if (!isInKnownSynchronizedContext(reference)) {
              areAllReferencesSynchronized = false;
            }
          }
        }
        if (areAllReferencesSynchronized &&
            unusedMethods.contains(method)) {
          methodsAlwaysSynchronized.add(method);
          methodsDeterminedThisPass.add(method);
          stabilized = false;
        }
      }
      remainingMethods.removeAll(methodsDeterminedThisPass);
    }
    methodsAlwaysSynchronized.addAll(remainingMethods);
  }

  private void determineUsedMethods(
    Set<PsiMethod> privateMethods,
    HashMap<PsiMethod, Collection<PsiReference>> referenceMap) {
    final Set<PsiMethod> remainingMethods =
      new HashSet<PsiMethod>(privateMethods);
    boolean stabilized = false;
    while (!stabilized) {
      stabilized = true;
      final Set<PsiMethod> methodsDeterminedThisPass =
        new HashSet<PsiMethod>();
      for (PsiMethod method : remainingMethods) {
        final Collection<PsiReference> references =
          referenceMap.get(method);
        for (PsiReference reference : references) {
          if (isKnownToBeUsed(reference)) {
            usedMethods.add(method);
            methodsDeterminedThisPass.add(method);
            stabilized = false;
          }
        }
      }
      remainingMethods.removeAll(methodsDeterminedThisPass);
    }
    unusedMethods.addAll(remainingMethods);
  }

  private static HashMap<PsiMethod, Collection<PsiReference>>
  buildReferenceMap(Set<PsiMethod> privateMethods) {
    final HashMap<PsiMethod, Collection<PsiReference>> referenceMap =
      new HashMap();
    for (PsiMethod method : privateMethods) {
      final SearchScope scope = method.getUseScope();
      final Collection<PsiReference> references =
        ReferencesSearch.search(method, scope).findAll();
      referenceMap.put(method, references);
    }
    return referenceMap;
  }

  private Set<PsiMethod> findPrivateMethods() {
    final Set<PsiMethod> privateMethods = new HashSet<PsiMethod>();
    final PsiMethod[] methods = aClass.getMethods();
    for (PsiMethod method : methods) {
      if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
        privateMethods.add(method);
      }
    }
    return privateMethods;
  }

  private boolean isKnownToBeUsed(PsiReference reference) {
    final PsiElement element = reference.getElement();

    final PsiMethod method =
      PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    if (method == null) {
      return true;
    }
    if (!method.hasModifierProperty(PsiModifier.PRIVATE)) {
      return true;
    }
    return usedMethods.contains(method);
  }

  private boolean isInKnownSynchronizedContext(PsiReference reference) {
    final PsiElement element = reference.getElement();
    if (PsiTreeUtil.getParentOfType(element,
                                    PsiSynchronizedStatement.class) != null) {
      return true;
    }
    final PsiMethod method =
      PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    if (method == null) {
      return false;
    }
    if (method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
      return true;
    }
    if (methodsAlwaysSynchronized.contains(method)) {
      return true;
    }
    return !methodsNotAlwaysSynchronized.contains(method);
  }

  private boolean isInKnownUnsynchronizedContext(PsiReference reference) {
    final PsiElement element = reference.getElement();
    if (PsiTreeUtil.getParentOfType(element,
                                    PsiSynchronizedStatement.class) != null) {
      return false;
    }
    final PsiMethod method =
      PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    if (method == null) {
      return true;
    }
    if (method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
      return false;
    }
    if (!method.hasModifierProperty(PsiModifier.PRIVATE)) {
      return true;
    }
    if (methodsAlwaysSynchronized.contains(method)) {
      return false;
    }
    return methodsNotAlwaysSynchronized.contains(method);
  }

  @Override
  public void visitClassInitializer(@NotNull PsiClassInitializer initializer) {
    m_inInitializer = true;
    super.visitClassInitializer(initializer);
    m_inInitializer = false;
  }

  @Override
  public void visitField(@NotNull PsiField field) {
    m_inInitializer = true;
    super.visitField(field);
    m_inInitializer = false;
  }

  public Set<PsiField> getInappropriatelyAccessedFields() {
    final Set<PsiField> out =
      new HashSet<PsiField>(m_synchronizedAccesses);
    out.retainAll(m_unsynchronizedAccesses);
    return out;
  }
}
