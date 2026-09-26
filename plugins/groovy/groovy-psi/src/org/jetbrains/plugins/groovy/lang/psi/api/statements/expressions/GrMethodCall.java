// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions;

import com.intellij.navigation.NavigationItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCallReference;

public interface GrMethodCall extends GrCallExpression, NavigationItem {
  @Nullable
  GroovyMethodCallReference getImplicitCallReference();

  @Nullable
  GroovyMethodCallReference getExplicitCallReference();

  @Nullable
  GroovyMethodCallReference getCallReference();

  @NotNull
  GrExpression getInvokedExpression();

  @Override
  @NotNull
  GrArgumentList getArgumentList();

  boolean isCommandExpression();
}
