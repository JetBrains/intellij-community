// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.assignment;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.findUsages.LiteralConstructorReference;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

public class GrListOrMapInfo implements ConstructorCallInfo<GrListOrMap> {
  private final GrListOrMap myListOrMap;
  private final LiteralConstructorReference myReference;

  public GrListOrMapInfo(GrListOrMap listOrMap) {
    myListOrMap = listOrMap;

    assert listOrMap.getReference() instanceof LiteralConstructorReference;
    myReference = ((LiteralConstructorReference)listOrMap.getReference());
  }

  @Nullable
  @Override
  public GrArgumentList getArgumentList() {
    return null;
  }

  @Nullable
  @Override
  public PsiType[] getArgumentTypes() {
    if (myListOrMap.isMap()) {
      GrNamedArgument[] args = myListOrMap.getNamedArguments();
      if (args.length == 0) return new PsiType[]{myListOrMap.getType()};

      return PsiUtil.getArgumentTypes(args, GrExpression.EMPTY_ARRAY, GrClosableBlock.EMPTY_ARRAY, true, null);
    }
    else {
      GrExpression[] args = myListOrMap.getInitializers();
      return PsiUtil.getArgumentTypes(GrNamedArgument.EMPTY_ARRAY, args, GrClosableBlock.EMPTY_ARRAY, true, null);
    }
  }

  @Nullable
  @Override
  public GrExpression getInvokedExpression() {
    return null;
  }

  @Nullable
  @Override
  public PsiType getQualifierInstanceType() {
    return null;
  }

  @NotNull
  @Override
  public PsiElement getElementToHighlight() {
    return myListOrMap;
  }

  @NotNull
  @Override
  public GroovyResolveResult advancedResolve() {
    return PsiImplUtil.extractUniqueResult(multiResolve());
  }

  @NotNull
  @Override
  public GroovyResolveResult[] multiResolve() {
    GroovyResolveResult[] results = myReference.multiResolve(false);
    if (results.length == 1 && results[0].getElement() instanceof PsiClass) {
      return GroovyResolveResult.EMPTY_ARRAY; //the same behaviour as constructor calls
    }
    return results;
  }

  @NotNull
  @Override
  public GrListOrMap getCall() {
    return myListOrMap;
  }

  @NotNull
  @Override
  public GrExpression[] getExpressionArguments() {
    return myListOrMap.isMap() ? GrExpression.EMPTY_ARRAY : myListOrMap.getInitializers();
  }

  @NotNull
  @Override
  public GrClosableBlock[] getClosureArguments() {
    return GrClosableBlock.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public GrNamedArgument[] getNamedArguments() {
    return myListOrMap.isMap() ? myListOrMap.getNamedArguments() : GrNamedArgument.EMPTY_ARRAY;
  }
}
