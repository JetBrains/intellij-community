/*
 * Copyright 2000-2007 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic.TypesUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @author ilyas
 */
public class GrMethodCallImpl extends GrExpressionImpl implements GrMethodCall {

  public GrMethodCallImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Method call";
  }

  public PsiType getType() {
    GrExpression invoked = getInvokedExpression();
    if (invoked instanceof GrReferenceExpression) {
      PsiElement resolved = ((GrReferenceExpression) invoked).resolve();
      if (resolved instanceof PsiMethod && resolved.getCopyableUserData(ResolveUtil.IS_BEING_RESOLVED) == null) {
        PsiType returnType = ((PsiMethod) resolved).getReturnType();
        return TypesUtil.boxPrimitiveTypeAndEraseGenerics(returnType, getManager(), getResolveScope());
      }
    }

    return null;
  }

  public GrArgumentList getArgumentList() {
    return findChildByClass(GrArgumentList.class);
  }

  public GrNamedArgument[] getNamedArguments() {
    GrArgumentList argList = getArgumentList();
    return argList != null ? argList.getNamedArguments() : GrNamedArgument.EMPTY_ARRAY;
  }

  public GrExpression[] getExpressionArguments() {
    GrArgumentList argList = getArgumentList();
    return argList != null ? argList.getExpressionArguments() : GrExpression.EMPTY_ARRAY;
  }

  public GrClosableBlock getClosureArgument() {
    return findChildByClass(GrClosableBlock.class);
  }

  public GrExpression getInvokedExpression() {
    return findChildByClass(GrExpression.class);
  }

  // TODO implement me!
  public GrArgumentList replaceArgumenList(GrArgumentList argList) throws IncorrectOperationException {
    return null;
  }

}