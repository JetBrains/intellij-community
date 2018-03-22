// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class GrSuperReferenceResolver {
  @Nullable("null if ref is not 'super' reference")
  public static Collection<GroovyResolveResult> resolveSuperExpression(@NotNull GrReferenceExpression ref) {
    GrExpression qualifier = ref.getQualifier();

    if (qualifier == null) {
      final PsiElement parent = ref.getParent();
      if (parent instanceof GrConstructorInvocation) {
        return Arrays.asList(((GrConstructorInvocation)parent).multiResolve(false));
      }
      PsiClass aClass = PsiUtil.getContextClass(ref);
      if (aClass != null) {
        return getSuperClass(aClass);
      }
    }
    else if (qualifier instanceof GrReferenceExpression) {
      GroovyResolveResult result = ((GrReferenceExpression)qualifier).advancedResolve();
      PsiElement resolved = result.getElement();
      if (resolved instanceof PsiClass) {
        PsiClass superClass = (PsiClass)resolved;

        GrTypeDefinition scopeClass = PsiTreeUtil.getParentOfType(ref, GrTypeDefinition.class, true);
        if (scopeClass != null && GrTraitUtil.isTrait(superClass) && scopeClass.isInheritor(superClass, false)) {
          PsiSubstitutor superClassSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, scopeClass, PsiSubstitutor.EMPTY);
          return Collections.singletonList(new GroovyResolveResultImpl(superClass, null, null, superClassSubstitutor, true, true));
        }

        if (PsiUtil.hasEnclosingInstanceInScope(superClass, ref, false)) {
          return getSuperClass(superClass);
        }
      }
    }

    return null;
  }

  @NotNull
  private static Collection<GroovyResolveResult> getSuperClass(@NotNull PsiClass aClass) {
    PsiClass superClass = aClass.getSuperClass();
    if (superClass != null) {
      PsiSubstitutor superClassSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass, PsiSubstitutor.EMPTY);
      return Collections.singletonList(new GroovyResolveResultImpl(superClass, null, null, superClassSubstitutor, true, true));
    }
    else {
      return Collections.emptyList(); //no super class, but the reference is definitely super-reference
    }
  }
}
