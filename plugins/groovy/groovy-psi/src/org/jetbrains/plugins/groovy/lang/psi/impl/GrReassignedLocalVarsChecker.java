// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.searches.ReferencesSearch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.CompileStaticUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.Collection;

public final class GrReassignedLocalVarsChecker {

  @Nullable
  public static PsiType getReassignedVarType(@NotNull GrReferenceExpression refExpr, boolean honorCompileStatic) {
    if (honorCompileStatic && !CompileStaticUtil.isCompileStatic(refExpr) || refExpr.getQualifier() != null) {
      return null;
    }

    final PsiElement resolved = refExpr.resolve();
    if (!PsiUtil.isLocalVariable(resolved)) {
      return null;
    }

    assert resolved instanceof GrVariable;

    return TypeInferenceHelper.getCurrentContext().getExpressionType(((GrVariable)resolved), variable -> getLeastUpperBoundByVar(variable));
  }

  @Nullable
  private static PsiType getLeastUpperBoundByVar(@NotNull final GrVariable var) {
    return RecursionManager.doPreventingRecursion(var, false, (NullableComputable<PsiType>)() -> {
      final Collection<PsiReference> all = ReferencesSearch.search(var, var.getUseScope()).findAll();
      final GrExpression initializer = var.getInitializerGroovy();

      if (initializer == null && all.isEmpty()) {
        return var.getDeclaredType();
      }

      PsiType result = initializer != null ? initializer.getType() : null;

      final PsiManager manager = var.getManager();
      for (PsiReference reference : all) {
        final PsiElement ref = reference.getElement();
        if (ref instanceof GrReferenceExpression && PsiUtil.isLValue(((GrReferenceExpression)ref))) {
          result = TypesUtil.getLeastUpperBoundNullable(result, TypeInferenceHelper.getInitializerTypeFor(ref), manager);
        }
      }

      return result;
    });
  }

}
