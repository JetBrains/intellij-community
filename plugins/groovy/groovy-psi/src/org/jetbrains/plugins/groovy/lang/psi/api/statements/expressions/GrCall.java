// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions;

import com.intellij.psi.PsiMethod;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

/**
 * @author ven
 */
public interface GrCall extends GroovyPsiElement {
  @Nullable
  GrArgumentList getArgumentList();

  @NotNull
  GrNamedArgument[] getNamedArguments();

  @NotNull
  GrExpression[] getExpressionArguments();

  default boolean hasClosureArguments() {
    return getClosureArguments().length > 0;
  }

  @NotNull
  default GrClosableBlock[] getClosureArguments() {
    return GrClosableBlock.EMPTY_ARRAY;
  }

  @Nullable
  GrNamedArgument addNamedArgument(GrNamedArgument namedArgument) throws IncorrectOperationException;

  @NotNull
  GroovyResolveResult[] getCallVariants(@Nullable GrExpression upToArgument);

  @Nullable
  default PsiMethod resolveMethod() {
    return PsiImplUtil.extractUniqueElement(multiResolve(false));
  }

  @NotNull
  default GroovyResolveResult advancedResolve() {
    return PsiImplUtil.extractUniqueResult(multiResolve(false));
  }

  @NotNull
  GroovyResolveResult[] multiResolve(boolean incompleteCode);
}
