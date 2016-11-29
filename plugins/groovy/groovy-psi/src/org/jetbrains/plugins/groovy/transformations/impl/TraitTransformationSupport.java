/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.transformations.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.PsiClassType.ClassResolveResult;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.PairConsumer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrTraitField;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrTraitMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GrClassImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil;
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport;
import org.jetbrains.plugins.groovy.transformations.TransformationContext;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;

public class TraitTransformationSupport implements AstTransformationSupport {

  @Override
  public void applyTransformation(@NotNull TransformationContext context) {
    if (context.getCodeClass().isInterface() && !context.getCodeClass().isTrait()) return;

    if (context.getCodeClass().isTrait()) {
      for (GrField field : context.getCodeClass().getCodeFields()) {
        context.addField(new GrTraitField(field, context.getCodeClass(), PsiSubstitutor.EMPTY));
      }
    }

    process(context, (trait, substitutor) -> {
      if (trait instanceof GrTypeDefinition) {
        for (PsiMethod method : trait.getMethods()) {
          if (!method.getModifierList().hasExplicitModifier(PsiModifier.ABSTRACT)) {
            context.addMethods(getExpandingMethods(context.getCodeClass(), method, substitutor));
          }
        }
        for (GrField field : ((GrTypeDefinition)trait).getCodeFields()) {
          context.addField(new GrTraitField(field, context.getCodeClass(), substitutor));
        }
      }
      else if (trait instanceof ClsClassImpl) {
        for (PsiMethod method : GrTraitUtil.getCompiledTraitConcreteMethods((ClsClassImpl)trait)) {
          context.addMethods(getExpandingMethods(context.getCodeClass(), method, substitutor));
        }
        for (GrField field : GrTraitUtil.getCompiledTraitFields((ClsClassImpl)trait)) {
          context.addField(new GrTraitField(field, context.getCodeClass(), substitutor));
        }
      }
    });
  }

  private static void process(@NotNull TransformationContext context, @NotNull PairConsumer<PsiClass, PsiSubstitutor> consumer) {
    Deque<Pair<PsiClass, PsiSubstitutor>> stack = new ArrayDeque<>();

    for (PsiClassType superType : context.getSuperTypes()) {
      ClassResolveResult result = superType.resolveGenerics();
      PsiClass superClass = result.getElement();
      if (superClass == null) continue;
      stack.push(Pair.create(superClass, result.getSubstitutor()));
    }

    Set<PsiClass> visited = ContainerUtil.newHashSet();

    while (!stack.isEmpty()) {
      Pair<PsiClass, PsiSubstitutor> current = stack.pop();
      PsiClass currentClass = current.first;
      PsiSubstitutor currentSubstitutor = current.second;
      if (!visited.add(currentClass)) continue;
      if (GrTraitUtil.isTrait(currentClass)) {
        consumer.consume(currentClass, currentSubstitutor);
      }
      for (PsiClass superClass : currentClass.getSupers()) {
        PsiSubstitutor superSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, currentClass, currentSubstitutor);
        stack.push(Pair.create(superClass, superSubstitutor));
      }
    }
  }

  @NotNull
  private static List<PsiMethod> getExpandingMethods(@NotNull PsiClass containingClass,
                                                     @NotNull PsiMethod method,
                                                     @NotNull PsiSubstitutor substitutor) {
    List<PsiMethod> result = ContainerUtil.newSmartList();
    for (PsiMethod expanded : GrClassImplUtil.expandReflectedMethods(method)) {
      result.add(new GrTraitMethod(containingClass, expanded, substitutor));
    }
    return result;
  }
}

