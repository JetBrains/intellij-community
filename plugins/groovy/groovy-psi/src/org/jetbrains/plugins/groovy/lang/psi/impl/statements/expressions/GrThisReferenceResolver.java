// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ElementResolveResult;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class GrThisReferenceResolver {
  @Nullable("null if ref is not actually 'this' reference")
  public static Collection<GroovyResolveResult> resolveThisExpression(@NotNull GrReferenceExpression ref) {
    GrExpression qualifier = ref.getQualifier();

    if (qualifier == null) {
      final PsiElement parent = ref.getParent();
      if (parent instanceof GrConstructorInvocation) {
        return Arrays.asList(((GrConstructorInvocation)parent).multiResolve(false));
      }
      else {
        PsiClass aClass = PsiUtil.getContextClass(ref);
        if (aClass != null) {
          return Collections.singletonList(new ElementResolveResult<>(aClass));
        }
      }
    }
    else if (qualifier instanceof GrReferenceExpression) {
      GroovyResolveResult result = ((GrReferenceExpression)qualifier).advancedResolve();
      PsiElement resolved = result.getElement();
      if (resolved instanceof PsiClass && PsiUtil.hasEnclosingInstanceInScope((PsiClass)resolved, ref, false)) {
        return Collections.singletonList(result);
      }
    }

    return null;
  }
}
