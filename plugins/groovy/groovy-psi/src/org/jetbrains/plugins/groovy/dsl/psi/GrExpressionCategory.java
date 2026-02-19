// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.dsl.psi;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;

import java.util.Arrays;
import java.util.Collection;

public class GrExpressionCategory implements PsiEnhancerCategory{

  public static Collection<GrExpression> getArguments(GrCallExpression call) {
    return Arrays.asList(call.getExpressionArguments());
  }

  public static @Nullable PsiClass getClassType(GrExpression expr) {
    final PsiType type = expr.getType();
    if (type instanceof PsiClassType classType) {
      return classType.resolve();
    } else {
      final String text = type.getPresentableText();
      final Project project = expr.getProject();
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      return facade.findClass(text, GlobalSearchScope.allScope(project));
    }
  }

}
