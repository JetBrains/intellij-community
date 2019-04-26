// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.api;

import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;

/**
 * Represents a Groovy lambda expression body.
 */
public interface GrLambdaBody extends GrControlFlowOwner {

  @NotNull
  GrLambdaExpression getLambdaExpression();

  @Nullable
  PsiType getReturnType();
}
