// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;

/**
 * @author ilyas
 */
public interface GrClosableBlock extends GrFunctionalExpression, GrCodeBlock {
  GrClosableBlock[] EMPTY_ARRAY = new GrClosableBlock[0];

  String OWNER_NAME = "owner";
  String IT_PARAMETER_NAME = "it";

  @NotNull
  @Override
  PsiElement getLBrace();

  @Override
  @NotNull
  GrParameterList getParameterList();

  GrParameter addParameter(GrParameter parameter);

  boolean hasParametersSection();

  @Override
  @Nullable
  PsiType getReturnType();

  @NotNull
  @Override
  GrParameter[] getAllParameters();

  @Override
  @Nullable
  PsiElement getArrow();

  @Override
  boolean isVarArgs();

  @Override
  @Nullable
  PsiType getOwnerType();
}
