// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.groovy;

import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.structuralsearch.plugin.util.StructuralSearchScriptScope;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;

public class IdeaOpenApiNonCodeMembersContributor extends NonCodeMembersContributor {
  @Override
  public void processDynamicElements(@NotNull PsiType qualifierType,
                                     @NotNull PsiScopeProcessor processor,
                                     @NotNull PsiElement place,
                                     @NotNull ResolveState state) {
    GlobalSearchScope scope = place.getResolveScope();
    if (scope instanceof StructuralSearchScriptScope) {
      PsiPackage openApiPackage = JavaPsiFacade.getInstance(place.getProject()).findPackage("com.intellij.psi");
      if (openApiPackage != null) {
        traversePackage(openApiPackage, c -> {
          processor.execute(c, state);
          c.processDeclarations(processor, state, null, place);
        });
      }
    }
  }

  private static void traversePackage(PsiPackage psiPackage, Consumer<? super PsiClass> classConsumer) {
    for (PsiPackage aPackage : psiPackage.getSubPackages()) {
      traversePackage(aPackage, classConsumer);
    }
    for (PsiClass psiClass : psiPackage.getClasses()) {
      classConsumer.consume(psiClass);
    }
  }
}
