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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

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
    final GroovyPsiManager grPsiManager = GroovyPsiManager.getInstance(project);

    final List<PsiMethod> allMethods = grPsiManager.getDefaultMethods(psiClass);

    final MethodSignature signature = method.getHierarchicalMethodSignature();
    /*final MethodResolverProcessor processor = new MethodResolverProcessor(method.getName(), ((GrMethod)method), false,
                                                                          JavaPsiFacade.getElementFactory(project).createType(psiClass),
                                                                          method.getSignature(PsiSubstitutor.EMPTY).getParameterTypes(),
                                                                          PsiType.EMPTY_ARRAY);
    for (PsiMethod m : allMethods) {
      if (PsiImplUtil.isExtendsSignature(m.getSignature(PsiSubstitutor.EMPTY), signature)) {
        processor.execute(m, ResolveState.initial());
      }
    }

    final GroovyResolveResult[] groovyResolveResults = processor.getCandidates();
    for (GroovyResolveResult groovyResolveResult : groovyResolveResults) {
      if (!consumer.process(((GrGdkMethod)groovyResolveResult.getElement()).getStaticMethod().getHierarchicalMethodSignature())) {
        return false;
      }
    }
  
    return true;*/

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
        final GrGdkMethod m1 = (GrGdkMethod)o1;
        final GrGdkMethod m2 = (GrGdkMethod)o2;

        final PsiType type1 = TypeConversionUtil.erasure(m1.getStaticMethod().getParameterList().getParameters()[0].getType());
        final PsiType type2 = TypeConversionUtil.erasure(m2.getStaticMethod().getParameterList().getParameters()[0].getType());
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
      if (!consumer.process(((GrGdkMethod)psiMethod).getStaticMethod().getHierarchicalMethodSignature())) {
        return false;
      }
    }
    return true;
  }
}
