/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.MethodResolverProcessor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public class GDKSuperMethodSearcher implements QueryExecutor<MethodSignatureBackedByPsiMethod, SuperMethodsSearch.SearchParameters> {
  public boolean execute(SuperMethodsSearch.SearchParameters queryParameters, Processor<MethodSignatureBackedByPsiMethod> consumer) {
    final PsiMethod method = queryParameters.getMethod();
    if (!(method instanceof GrMethod)) {
      return true;
    }

    final PsiClass psiClass = method.getContainingClass();
    if (psiClass == null) return true;

    final HierarchicalMethodSignature hierarchicalSignature = method.getHierarchicalMethodSignature();
    if (hierarchicalSignature.getSuperSignatures().size() != 0) return true;

    final Project project = method.getProject();

    final String name = method.getName();
    final MethodResolverProcessor processor = new MethodResolverProcessor(name, ((GrMethod)method), false, null, null, PsiType.EMPTY_ARRAY);
    ResolveUtil.processNonCodeMethods(JavaPsiFacade.getElementFactory(project).createType(psiClass), processor, method);

    final GroovyResolveResult[] candidates = processor.getCandidates();

    final List<PsiMethod> allMethods = new ArrayList<PsiMethod>();
    for (GroovyResolveResult candidate : candidates) {
      final PsiElement element = candidate.getElement();
      if (element instanceof PsiMethod) {
        allMethods.add((PsiMethod)element);
      }
    }

    final MethodSignature signature = method.getHierarchicalMethodSignature();

    List<PsiMethod> goodSupers = new ArrayList<PsiMethod>();
    for (PsiMethod m : allMethods) {
      if (PsiImplUtil.isExtendsSignature(m.getHierarchicalMethodSignature(), signature)) {
        goodSupers.add(m);
      }
    }
    if (goodSupers.size() == 0) return true;

    final PsiManager psiManager = PsiManager.getInstance(project);
    final GlobalSearchScope searchScope = GlobalSearchScope.allScope(project);

    List<PsiMethod> result = new ArrayList<PsiMethod>(goodSupers.size());
    result.add(goodSupers.get(0));

    final Comparator<PsiMethod> comparator = new Comparator<PsiMethod>() {
      public int compare(PsiMethod o1, PsiMethod o2) { //compare by first parameter type
        o1 = getRealMethod(o1);
        o2 = getRealMethod(o2);

        final PsiType type1 = TypeConversionUtil.erasure(o1.getParameterList().getParameters()[0].getType());
        final PsiType type2 = TypeConversionUtil.erasure(o2.getParameterList().getParameters()[0].getType());
        if (TypesUtil.isAssignable(type1, type2, psiManager, searchScope)) {
          return -1;
        }
        else if (TypesUtil.isAssignable(type2, type1, psiManager, searchScope)) {
          return 1;
        }
        return 0;
      }
    };

    Outer:
    for (PsiMethod current : goodSupers) {
      for (Iterator<PsiMethod> i = result.iterator(); i.hasNext();) {
        PsiMethod m = i.next();
        final int res = comparator.compare(m, current);
        if (res > 0) {
          continue Outer;
        }
        else if (res < 0) {
          i.remove();
        }
      }
      result.add(current);
    }
    for (PsiMethod psiMethod : result) {
      if (!consumer.process(getRealMethod(psiMethod).getHierarchicalMethodSignature())) {
        return false;
      }
    }
    return true;
  }

  private static PsiMethod getRealMethod(PsiMethod method) {
    final PsiElement element = method.getNavigationElement();
    if (element instanceof PsiMethod) {
      return (PsiMethod)element;
    }
    else {
      return method;
    }
  }
}
