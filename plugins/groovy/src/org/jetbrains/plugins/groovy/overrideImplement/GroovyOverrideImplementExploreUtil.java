/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.overrideImplement;

import com.intellij.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrTraitMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created by Max Medvedev on 16/05/14
 */
public class GroovyOverrideImplementExploreUtil {

  @NotNull
  public static Collection<MethodSignature> getMethodSignaturesToOverride(@NotNull GrTypeDefinition aClass) {
    if (aClass.isAnnotationType()) return Collections.emptySet();
    return getMapToOverrideImplement(aClass, false, true).keySet();
  }

  @NotNull
  public static Collection<MethodSignature> getMethodSignaturesToImplement(@NotNull GrTypeDefinition aClass) {
    return getMapToOverrideImplement(aClass, true, true).keySet();
  }

  @NotNull
  public static Collection<CandidateInfo> getMethodsToOverrideImplement(GrTypeDefinition aClass, boolean toImplement) {
    return getMapToOverrideImplement(aClass, toImplement, true).values();
  }

  @NotNull
  public static Map<MethodSignature, CandidateInfo> getMapToOverrideImplement(GrTypeDefinition aClass, boolean toImplement, boolean skipImplemented) {
    Map<MethodSignature, PsiMethod> abstracts = ContainerUtil.newLinkedHashMap();
    Map<MethodSignature, PsiMethod> finals = ContainerUtil.newLinkedHashMap();
    Map<MethodSignature, PsiMethod> concretes = ContainerUtil.newLinkedHashMap();

    PsiUtilCore.ensureValid(aClass);
    Collection<HierarchicalMethodSignature> allMethodSignatures = aClass.getVisibleSignatures();
    PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(aClass.getProject()).getResolveHelper();
    for (HierarchicalMethodSignature signature : allMethodSignatures) {
      PsiMethod method = signature.getMethod();
      if (method instanceof GrTraitMethod) {
        for (HierarchicalMethodSignature superSignature : signature.getSuperSignatures()) {
          processMethod(aClass, skipImplemented, abstracts, finals, concretes, resolveHelper, superSignature, superSignature.getMethod());
        }
      }
      else {
        processMethod(aClass, skipImplemented, abstracts, finals, concretes, resolveHelper, signature, method);
      }
    }

    final Map<MethodSignature, CandidateInfo> result = new TreeMap<>(new OverrideImplementExploreUtil.MethodSignatureComparator());
    if (toImplement || aClass.isInterface()) {
      collectMethodsToImplement(aClass, abstracts, finals, concretes, result);
    }
    else {
      for (Map.Entry<MethodSignature, PsiMethod> entry : concretes.entrySet()) {
        MethodSignature signature = entry.getKey();
        PsiMethod concrete = entry.getValue();
        if (finals.get(signature) == null) {
          PsiMethod abstractOne = abstracts.get(signature);
          if (abstractOne == null || !abstractOne.getContainingClass().isInheritor(concrete.getContainingClass(), true) ||
              CommonClassNames.JAVA_LANG_OBJECT.equals(concrete.getContainingClass().getQualifiedName())) {
            PsiSubstitutor subst = OverrideImplementExploreUtil.correctSubstitutor(concrete, signature.getSubstitutor());
            CandidateInfo info = new CandidateInfo(concrete, subst);
            result.put(signature, info);
          }
        }
      }
    }

    return result;
  }

  private static void processMethod(GrTypeDefinition aClass,
                                    boolean skipImplemented,
                                    Map<MethodSignature, PsiMethod> abstracts,
                                    Map<MethodSignature, PsiMethod> finals,
                                    Map<MethodSignature, PsiMethod> concretes,
                                    PsiResolveHelper resolveHelper, HierarchicalMethodSignature signature, PsiMethod method) {
    PsiUtilCore.ensureValid(method);

    if (method.hasModifierProperty(PsiModifier.STATIC) || !resolveHelper.isAccessible(method, aClass, aClass)) return;
    PsiClass hisClass = method.getContainingClass();
    if (hisClass == null) return;
    // filter non-immediate super constructors
    if (method.isConstructor() && (!aClass.isInheritor(hisClass, false) || aClass instanceof PsiAnonymousClass || aClass.isEnum())) {
      return;
    }
    // filter already implemented
    if (skipImplemented) {
      PsiMethod implemented = MethodSignatureUtil.findMethodBySignature(aClass, signature, false);
      if (implemented != null && !(implemented instanceof GrTraitMethod)) {
        return;
      }
    }

    if (method.hasModifierProperty(PsiModifier.FINAL)) {
      finals.put(signature, method);
      return;
    }

    Map<MethodSignature, PsiMethod> map = method.hasModifierProperty(PsiModifier.ABSTRACT) ? abstracts : concretes;
    fillMap(signature, method, map);
    if (isDefaultMethod(method)) {
      fillMap(signature, method, concretes);
    }
  }

  private static void fillMap(HierarchicalMethodSignature signature, PsiMethod method, Map<MethodSignature, PsiMethod> map) {
    final PsiMethod other = map.get(signature);
    if (other == null || preferLeftForImplement(method, other)) {
      map.put(signature, method);
    }
  }

  private static boolean preferLeftForImplement(PsiMethod left, PsiMethod right) {
    if (PsiUtil.getAccessLevel(left.getModifierList()) > PsiUtil.getAccessLevel(right.getModifierList())) return true;
    PsiClass lClass = left.getContainingClass();
    PsiClass rClass = right.getContainingClass();
    if (lClass != null && !lClass.isInterface()) return true;
    if (rClass != null && !rClass.isInterface()) return false;
    // implement annotated method
    PsiAnnotation[] leftAnnotations = left.getModifierList().getAnnotations();
    PsiAnnotation[] rightAnnotations = right.getModifierList().getAnnotations();
    return leftAnnotations.length > rightAnnotations.length;
  }

  private static boolean isDefaultMethod(PsiMethod method) {
    return method instanceof GrMethod && !method.getModifierList().hasExplicitModifier(PsiModifier.ABSTRACT) &&
           GrTraitUtil.isTrait(method.getContainingClass());
  }

  public static void collectMethodsToImplement(PsiClass aClass,
                                               Map<MethodSignature, PsiMethod> abstracts,
                                               Map<MethodSignature, PsiMethod> finals,
                                               Map<MethodSignature, PsiMethod> concretes,
                                               Map<MethodSignature, CandidateInfo> result) {
    for (Map.Entry<MethodSignature, PsiMethod> entry : abstracts.entrySet()) {
      MethodSignature signature = entry.getKey();
      PsiMethod abstractOne = entry.getValue();
      PsiMethod concrete = concretes.get(signature);
      if (concrete == null
          || PsiUtil.getAccessLevel(concrete.getModifierList()) < PsiUtil.getAccessLevel(abstractOne.getModifierList())
          || !abstractOne.getContainingClass().isInterface() && abstractOne.getContainingClass().isInheritor(concrete.getContainingClass(), true)
          || isDefaultMethod(abstractOne)) {
        if (finals.get(signature) == null) {
          PsiSubstitutor subst = OverrideImplementExploreUtil.correctSubstitutor(abstractOne, signature.getSubstitutor());
          CandidateInfo info = new CandidateInfo(abstractOne, subst);
          result.put(signature, info);
        }
      }
    }

    for (final PsiMethod method : new GroovyMethodImplementor().getMethodsToImplement(aClass)) {
      MethodSignature signature = MethodSignatureUtil.createMethodSignature(method.getName(), method.getParameterList(),
                                                                            method.getTypeParameterList(), PsiSubstitutor.EMPTY,
                                                                            method.isConstructor());
      CandidateInfo info = new CandidateInfo(method, PsiSubstitutor.EMPTY);
      result.put(signature, info);
    }
  }

}
