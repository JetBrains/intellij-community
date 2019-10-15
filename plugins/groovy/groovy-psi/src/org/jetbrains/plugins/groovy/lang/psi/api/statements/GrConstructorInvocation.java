// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.api.statements;

import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConstructorCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyCallReference;

public interface GrConstructorInvocation extends GrStatement, GrConstructorCall {

  @Override
  @NotNull
  GroovyCallReference getConstructorReference();

  boolean isSuperCall();

  boolean isThisCall();

  @NotNull
  GrReferenceExpression getInvokedExpression();

  @Nullable
  PsiClass getDelegatedClass();

  @Override
  @NotNull
  GrArgumentList getArgumentList();
}
