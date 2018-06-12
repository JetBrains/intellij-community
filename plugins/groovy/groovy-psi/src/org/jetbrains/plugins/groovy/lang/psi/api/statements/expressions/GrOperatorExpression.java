// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions;

import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyReference;

public interface GrOperatorExpression extends GrExpression, GroovyReference {

  @Nullable
  PsiType getLeftType();

  @Nullable
  PsiType getRightType();

  @Nullable
  IElementType getOperator();
}
