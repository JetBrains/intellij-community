/*
 * Copyright 2003-2016 Dave Griffith, Bas Leijdekkers
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

import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.HashMap;
import com.siyeh.ig.psiutils.SynchronizationUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

class VariableAccessVisitor extends JavaRecursiveElementWalkingVisitor {

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
  private boolean m_inInitializer;
  private int m_inSynchronizedContextCount;
  private final Stack<Integer> contextStack = new Stack<Integer>();
  private final Stack<Boolean> contextInitializerStack = new Stack<Boolean>();
  private boolean privateMethodUsagesCalculated;
  private final boolean countGettersAndSetters;

  VariableAccessVisitor(@NotNull PsiClass aClass, boolean countGettersAndSetters) {
    this.aClass = aClass;
    this.countGettersAndSetters = countGettersAndSetters;
  }

  @Override
  public void visitClass(PsiClass classToVisit) {
    calculatePrivateMethodUsagesIfNecessary();
    if (!classToVisit.equals(aClass)) {
      contextStack.push(m_inSynchronizedContextCount);
      m_inSynchronizedContextCount = 0;
      
      contextInitializerStack.push(m_inInitializer);
      m_inInitializer = false;
    }
    super.visitClass(classToVisit);
  }

  @Override
  public void visitLambdaExpression(PsiLambdaExpression expression) {
    contextStack.push(m_inSynchronizedContextCount);
    m_inSynchronizedContextCount = 0;
    
    contextInitializerStack.push(m_inInitializer);
    m_inInitializer = false;
    super.visitLambdaExpression(expression);
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
    else if (m_inSynchronizedContextCount > 0) {
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
    else if (m_inSynchronizedContextCount > 0) {
      m_synchronizedAccesses.add(field);
    }
    else {
      m_unsynchronizedAccesses.add(field);
    }
  }

  @Override
  public void visitCodeBlock(PsiCodeBlock block) {
    if (block.getParent() instanceof PsiSynchronizedStatement) {
      m_inSynchronizedContextCount++;
    }
    super.visitCodeBlock(block);
  }

  private static final Key<Boolean> CODE_BLOCK_CONTAINS_HOLDS_LOCK_CALL = Key.create("CODE_BLOCK_CONTAINS_HOLDS_LOCK_CALL");
  @Override
  public void visitAssertStatement(PsiAssertStatement statement) {
    final PsiExpression condition = statement.getAssertCondition();
    if (SynchronizationUtil.isCallToHoldsLock(condition)) {
      m_inSynchronizedContextCount++;
      PsiElement codeBlock = statement.getParent();
      if (codeBlock != null) {
        codeBlock.putUserData(CODE_BLOCK_CONTAINS_HOLDS_LOCK_CALL, true);
      }
    }
    super.visitAssertStatement(statement);
  }

  @Override
  public void visitMethod(@NotNull PsiMethod method) {
    if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
      if (unusedMethods.contains(method)) {
        return;
      }
    }
    final boolean methodIsSynchronized =
      method.hasModifierProperty(PsiModifier.SYNCHRONIZED)
      || methodIsAlwaysUsedSynchronized(method);
    if (methodIsSynchronized) {
      m_inSynchronizedContextCount++;
    }
    final boolean isConstructor = method.isConstructor();
    if (isConstructor) {
      m_inInitializer = true;
    }
    super.visitMethod(method);
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
      new HashMap<PsiMethod, Collection<PsiReference>>();
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
  }

  @Override
  public void visitField(@NotNull PsiField field) {
    m_inInitializer = true;
    super.visitField(field);
  }

  @Override
  protected void elementFinished(@NotNull PsiElement element) {
    if (element instanceof PsiField || element instanceof PsiClassInitializer || element instanceof PsiMethod && ((PsiMethod)element).isConstructor()) {
      m_inInitializer = false;
    }
    if (element instanceof PsiClass && !element.equals(aClass) || element instanceof PsiLambdaExpression) {
      m_inSynchronizedContextCount = contextStack.pop();
      m_inInitializer = contextInitializerStack.pop();
    }
    if (element instanceof PsiCodeBlock && element.getParent() instanceof PsiSynchronizedStatement) {
      m_inSynchronizedContextCount--;
    }
    else if (element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)element;
      if (method.hasModifierProperty(PsiModifier.SYNCHRONIZED) || methodIsAlwaysUsedSynchronized(method)) {
        m_inSynchronizedContextCount--;
      }
    }
    if (element.getUserData(CODE_BLOCK_CONTAINS_HOLDS_LOCK_CALL) != null) {
      m_inSynchronizedContextCount --;
      element.putUserData(CODE_BLOCK_CONTAINS_HOLDS_LOCK_CALL, null);
    }
  }

  Set<PsiField> getInappropriatelyAccessedFields() {
    final Set<PsiField> out =
      new HashSet<PsiField>(m_synchronizedAccesses);
    out.retainAll(m_unsynchronizedAccesses);
    return out;
  }
}
