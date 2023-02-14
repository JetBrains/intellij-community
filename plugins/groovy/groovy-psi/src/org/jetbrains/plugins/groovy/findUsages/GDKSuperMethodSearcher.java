// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.dgm.DGMMemberContributor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.MethodResolverProcessor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public class GDKSuperMethodSearcher implements QueryExecutor<MethodSignatureBackedByPsiMethod, SuperMethodsSearch.SearchParameters> {

  @Override
  public boolean execute(@NotNull SuperMethodsSearch.SearchParameters queryParameters, @NotNull Processor<? super MethodSignatureBackedByPsiMethod> consumer) {
    final PsiMethod method = queryParameters.getMethod();
    if (!(method instanceof GrMethod)) {
      return true;
    }

    if (method.hasModifierProperty(PsiModifier.STATIC)) return true;

    final PsiClass psiClass = method.getContainingClass();
    if (psiClass == null) return true;

    final HierarchicalMethodSignature hierarchicalSignature = method.getHierarchicalMethodSignature();
    if (!hierarchicalSignature.getSuperSignatures().isEmpty()) return true;

    final Project project = method.getProject();

    final String name = method.getName();
    final MethodResolverProcessor processor = new MethodResolverProcessor(name, method, false, null, null, PsiType.EMPTY_ARRAY);
    DGMMemberContributor.processDgmMethods(JavaPsiFacade.getElementFactory(project).createType(psiClass),
                                           processor, method, ResolveState.initial());

    final GroovyResolveResult[] candidates = processor.getCandidates();

    final PsiManager psiManager = PsiManager.getInstance(project);

    final MethodSignature signature = method.getHierarchicalMethodSignature();
    List<PsiMethod> goodSupers = new ArrayList<>();

    for (GroovyResolveResult candidate : candidates) {
      final PsiElement element = candidate.getElement();
      if (element instanceof PsiMethod m) {
        if (!isTheSameMethod(method, psiManager, m) && PsiImplUtil.isExtendsSignature(m.getHierarchicalMethodSignature(), signature)) {
          goodSupers.add(m);
        }
      }
    }

    if (goodSupers.isEmpty()) return true;

    List<PsiMethod> result = new ArrayList<>(goodSupers.size());
    result.add(goodSupers.get(0));

    final Comparator<PsiMethod> comparator = (o1, o2) -> { //compare by first parameter type
      final PsiType type1 = getRealType(o1);
      final PsiType type2 = getRealType(o2);
      if (TypesUtil.isAssignableByMethodCallConversion(type1, type2, o1)) {
        return -1;
      }
      else if (TypesUtil.isAssignableByMethodCallConversion(type2, type1, o1)) {
        return 1;
      }
      return 0;
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

  private static boolean isTheSameMethod(PsiMethod method, PsiManager psiManager, PsiMethod m) {
    return psiManager.areElementsEquivalent(m, method) || psiManager.areElementsEquivalent(m.getNavigationElement(), method);
  }

  private static PsiMethod getRealMethod(PsiMethod method) {
    final PsiElement element = method.getNavigationElement();
    if (element instanceof PsiMethod && !((PsiMethod)element).getParameterList().isEmpty()) {
      return (PsiMethod)element;
    }
    else {
      return method;
    }
  }

  @Nullable
  private static PsiType getRealType(PsiMethod method) {
    final PsiElement navigationElement = method.getNavigationElement();
    if (navigationElement instanceof PsiMethod) {
      final PsiParameter[] parameters = ((PsiMethod)navigationElement).getParameterList().getParameters();
      if (parameters.length != 0) {
        return TypeConversionUtil.erasure(parameters[0].getType());
      }
    }
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return null;
    return JavaPsiFacade.getElementFactory(method.getProject()).createType(containingClass);
  }

}
