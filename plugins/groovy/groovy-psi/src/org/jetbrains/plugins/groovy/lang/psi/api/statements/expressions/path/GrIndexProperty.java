// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyReference;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public interface GrIndexProperty extends GrExpression {

  @NotNull
  GrExpression getInvokedExpression();

  @Nullable
  PsiElement getSafeAccessToken();

  @NotNull
  GrArgumentList getArgumentList();

  @Nullable
  GroovyReference getLValueReference();

  @Nullable
  GroovyReference getRValueReference();
}
