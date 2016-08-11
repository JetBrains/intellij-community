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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
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

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class TraitTransformationSupport implements AstTransformationSupport {

  private static final Logger LOG = Logger.getInstance(TraitTransformationSupport.class);

  @Override
  public void applyTransformation(@NotNull TransformationContext context) {
    if (context.getCodeClass().isInterface() && !context.getCodeClass().isTrait()) return;

    if (context.getCodeClass().isTrait()) {
      for (GrField field : context.getCodeClass().getCodeFields()) {
        context.addField(new GrTraitField(field, context.getCodeClass(), PsiSubstitutor.EMPTY));
      }
    }

    List<PsiClassType.ClassResolveResult> traits = getSuperTraitsByCorrectOrder(context.getSuperTypes());
    if (traits.isEmpty()) return;

    for (PsiClassType.ClassResolveResult resolveResult : traits) {
      PsiClass superTrait = resolveResult.getElement();
      LOG.assertTrue(superTrait != null);

      process(superTrait, resolveResult.getSubstitutor(), ContainerUtil.newHashSet(), (trait, substitutor) -> {
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
  }

  private static void process(@NotNull PsiClass trait,
                              @NotNull PsiSubstitutor substitutor,
                              @NotNull Set<PsiClass> visited,
                              @NotNull PairConsumer<PsiClass, PsiSubstitutor> consumer) {
    consumer.consume(trait, substitutor);
    List<PsiClassType.ClassResolveResult> traits = getSuperTraitsByCorrectOrder(Arrays.asList(trait.getSuperTypes()));
    for (PsiClassType.ClassResolveResult resolveResult : traits) {
      PsiClass superClass = resolveResult.getElement();
      if (superClass != null && visited.add(superClass)) {
        final PsiSubstitutor superSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, trait, substitutor);
        process(superClass, superSubstitutor, visited, consumer);
      }
    }
  }

  @NotNull
  private static List<PsiClassType.ClassResolveResult> getSuperTraitsByCorrectOrder(@NotNull List<PsiClassType> types) {
    List<PsiClassType.ClassResolveResult> traits = ContainerUtil.newSmartList();
    for (int i = types.size() - 1; i >= 0; i--) {
      PsiClassType.ClassResolveResult resolveResult = types.get(i).resolveGenerics();
      PsiClass superClass = resolveResult.getElement();

      if (GrTraitUtil.isTrait(superClass)) {
        traits.add(resolveResult);
      }
    }
    return traits;
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

