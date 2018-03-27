// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParametersOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;

/**
 * @author ilyas
 */
public interface GrClosableBlock extends GrExpression, GrCodeBlock, GrParametersOwner {
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

  @Nullable
  PsiType getReturnType();

  GrParameter[] getAllParameters();

  @Nullable
  PsiElement getArrow();

  @Override
  boolean isVarArgs();

  boolean processClosureDeclarations(@NotNull final PsiScopeProcessor placeProcessor,
                                     @NotNull final ResolveState _state,
                                     @Nullable final PsiElement lastParent,
                                     @NotNull final PsiElement place);

  @Nullable
  PsiType getOwnerType();
}
