// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.api.statements.params;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiParameterList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

public interface GrParameterList extends GroovyPsiElement, PsiParameterList {

  @Nullable
  PsiElement getLParen();

  @Nullable
  PsiElement getRParen();

  @NotNull
  TextRange getParametersRange();

  @Override
  GrParameter @NotNull [] getParameters();

  int getParameterNumber(GrParameter parameter);
}
