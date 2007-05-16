/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.NameHint;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks.GrBlockImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrVariableImpl;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;

import java.util.List;

/**
 * @author ven
 */
public class ResolveUtil {
  public static boolean treeWalkUp(PsiElement place, PsiScopeProcessor processor) {
    PsiElement lastParent = null;
    while (place != null) {
      if (!place.processDeclarations(processor, PsiSubstitutor.EMPTY, lastParent, place)) return false;
      lastParent = place;
      place = place.getParent();
    }

    return true;
  }

  public static boolean processChildren(PsiElement element, PsiScopeProcessor processor,
                                        PsiSubstitutor substitutor, PsiElement lastParent, PsiElement place) {
    PsiElement run = lastParent == null ? element.getLastChild() : lastParent.getPrevSibling();
    while(run != null) {
      if (!run.processDeclarations(processor, substitutor, null, place)) return false;
      run = run.getPrevSibling();
    }

    return true;
  }

  public static boolean processElement(PsiScopeProcessor processor, GrNamedElement namedElement) {
    NameHint nameHint = processor.getHint(NameHint.class);
    String name = nameHint == null ? null : nameHint.getName();
    if (name == null || name.equals(namedElement.getName())) {
      return processor.execute(namedElement, PsiSubstitutor.EMPTY);
    }

    return true;
  }

  public static ClassHint.ResolveKind getResolveKind(PsiElement element) {
    if (element instanceof PsiVariable) return ClassHint.ResolveKind.PROPERTY;
    if (element instanceof GrVariable) return ClassHint.ResolveKind.PROPERTY;
    if (element instanceof GrReferenceExpression) return ClassHint.ResolveKind.PROPERTY;

    else if (element instanceof PsiMethod) return  ClassHint.ResolveKind.METHOD;

    else return ClassHint.ResolveKind.CLASS;
  }

  public static Object[] mapToElements(GroovyResolveResult[] candidates) {
    PsiElement[] elements = new PsiElement[candidates.length];
    for (int i = 0; i < elements.length; i++) {
      elements[i] = candidates[i].getElement();
    }

    return elements;
  }

  public static boolean isSuperMethodDominated(PsiMethod method, List<PsiMethod> worklist) {
    PsiParameter[] params = method.getParameterList().getParameters();
    PsiModifierList modifierList = method.getModifierList();

    NextMethod:
    for (PsiMethod other : worklist) {
      PsiParameter[] otherParams = other.getParameterList().getParameters();
      if (otherParams.length != params.length) continue;
      if (PsiUtil.getAccessLevel(other.getModifierList()) > PsiUtil.getAccessLevel(modifierList)) continue;
      for (int i = 0; i < params.length; i++) {
        PsiType type = params[i].getType();
        PsiType otherType = otherParams[i].getType();
        if (!type.isAssignableFrom(otherType)) continue NextMethod;
      }
      return true;
    }

    return false;
  }
}
