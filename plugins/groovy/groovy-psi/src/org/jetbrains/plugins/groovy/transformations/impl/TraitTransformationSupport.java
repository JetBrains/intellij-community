// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.transformations.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.PsiClassType.ClassResolveResult;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.PairConsumer;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrTraitField;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrTraitMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GrClassImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil;
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport;
import org.jetbrains.plugins.groovy.transformations.TransformationContext;

import java.util.*;

public class TraitTransformationSupport implements AstTransformationSupport {

  @Override
  public void applyTransformation(@NotNull TransformationContext context) {
    final GrTypeDefinition codeClass = context.getCodeClass();
    if (codeClass.isInterface() && !codeClass.isTrait()) return;

    if (codeClass.isTrait() && codeClass.getQualifiedName() != null) {
      for (GrField field : codeClass.getCodeFields()) {
        context.addField(new GrTraitField(field, codeClass, PsiSubstitutor.EMPTY, context));
      }
    }

    process(context, (trait, substitutor) -> {
      if (trait instanceof GrTypeDefinition) {
        for (PsiMethod method : trait.getMethods()) {
          if ((((GrTypeDefinition)trait).isTrait() || method.getModifierList().hasExplicitModifier(PsiModifier.DEFAULT)) &&
              !method.getModifierList().hasExplicitModifier(PsiModifier.ABSTRACT) &&
              !method.getModifierList().hasExplicitModifier(PsiModifier.PRIVATE)) {
            context.addMethods(getExpandingMethods(codeClass, method, substitutor));
          }
        }
        for (GrField field : ((GrTypeDefinition)trait).getCodeFields()) {
          context.addField(new GrTraitField(field, codeClass, substitutor, context));
        }
      }
      else if (trait instanceof ClsClassImpl) {
        for (PsiMethod method : GrTraitUtil.getCompiledTraitConcreteMethods((ClsClassImpl)trait)) {
          context.addMethods(getExpandingMethods(codeClass, method, substitutor));
        }
        for (GrField field : GrTraitUtil.getCompiledTraitFields((ClsClassImpl)trait)) {
          context.addField(new GrTraitField(field, codeClass, substitutor, context));
        }
      }
    });
  }

  private static void process(@NotNull TransformationContext context, @NotNull PairConsumer<? super PsiClass, ? super PsiSubstitutor> consumer) {
    Deque<Pair<PsiClass, PsiSubstitutor>> stack = new ArrayDeque<>();

    for (PsiClassType superType : context.getSuperTypes()) {
      ClassResolveResult result = superType.resolveGenerics();
      PsiClass superClass = result.getElement();
      if (superClass == null) continue;
      stack.push(Pair.create(superClass, result.getSubstitutor()));
    }

    Set<PsiClass> visited = new HashSet<>();

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
    List<PsiMethod> result = new SmartList<>();
    for (PsiMethod expanded : GrClassImplUtil.expandReflectedMethods(method)) {
      result.add(new GrTraitMethod(containingClass, expanded, substitutor));
    }
    return result;
  }
}

