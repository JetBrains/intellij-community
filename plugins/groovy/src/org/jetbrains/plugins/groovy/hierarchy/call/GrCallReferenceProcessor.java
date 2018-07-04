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
package org.jetbrains.plugins.groovy.hierarchy.call;

import com.intellij.ide.hierarchy.call.CallHierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.call.CallReferenceProcessor;
import com.intellij.ide.hierarchy.call.JavaCallHierarchyData;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;

import java.util.Map;
import java.util.Set;

public class GrCallReferenceProcessor implements CallReferenceProcessor {
  @Override
  public boolean process(@NotNull PsiReference reference, @NotNull JavaCallHierarchyData data) {
    PsiClass originalClass = data.getOriginalClass();
    PsiMethod method = data.getMethod();
    Set<PsiMethod> methodsToFind = data.getMethodsToFind();
    PsiMethod methodToFind = data.getMethodToFind();
    PsiClassType originalType = data.getOriginalType();
    Map<PsiMember, NodeDescriptor> methodToDescriptorMap = data.getResultMap();
    Project project = data.getProject();

    if (reference instanceof GrReferenceExpression) {
      final GrExpression qualifier = ((GrReferenceExpression)reference).getQualifierExpression();
      if (org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isSuperReference(qualifier)) { // filter super.foo() call inside foo() and similar cases (bug 8411)
        final PsiClass superClass = PsiUtil.resolveClassInType(qualifier.getType());
        if (originalClass == null || superClass == null || originalClass.isInheritor(superClass, true)) {
          return false;
        }
      }
      if (qualifier != null && !methodToFind.hasModifierProperty(PsiModifier.STATIC)) {
        final PsiType qualifierType = qualifier.getType();
        if (qualifierType instanceof PsiClassType && !TypeConversionUtil.isAssignable(qualifierType, originalType) && methodToFind != method) {
          final PsiClass psiClass = ((PsiClassType)qualifierType).resolve();
          if (psiClass != null) {
            final PsiMethod callee = psiClass.findMethodBySignature(methodToFind, true);
            if (callee != null && !methodsToFind.contains(callee)) {
              // skip sibling methods
              return false;
            }
          }
        }
      }
    }
    else {
      if (!(reference instanceof PsiElement)) {
        return true;
      }

      final PsiElement parent = ((PsiElement)reference).getParent();
      if (parent instanceof PsiNewExpression) {
        if (((PsiNewExpression)parent).getClassReference() != reference) {
          return true;
        }
      }
      else if (parent instanceof GrAnonymousClassDefinition) {
        if (((GrAnonymousClassDefinition)parent).getBaseClassReferenceGroovy() != reference) {
          return true;
        }
      }
      else {
        return true;
      }
    }

    final PsiElement element = reference.getElement();
    final PsiMember key = CallHierarchyNodeDescriptor.getEnclosingElement(element);

    synchronized (methodToDescriptorMap) {
      CallHierarchyNodeDescriptor d = (CallHierarchyNodeDescriptor)methodToDescriptorMap.get(key);
      if (d == null) {
        d = new CallHierarchyNodeDescriptor(project, (CallHierarchyNodeDescriptor)data.getNodeDescriptor(), element, false, true);
        methodToDescriptorMap.put(key, d);
      }
      else if (!d.hasReference(reference)) {
        d.incrementUsageCount();
      }
      d.addReference(reference);
    }
    return true;
  }
}
