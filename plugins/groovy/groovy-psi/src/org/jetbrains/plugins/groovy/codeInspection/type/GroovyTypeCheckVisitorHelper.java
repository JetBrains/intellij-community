// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.type;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrSpreadArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GroovyTypeCheckVisitorHelper {

  @Contract("null -> false")
  protected static boolean hasTupleInitializer(@Nullable GrExpression initializer) {
    return initializer instanceof GrListOrMap && ((GrListOrMap)initializer).getConstructorReference() != null;
  }

  @NotNull
  public static PsiElement getExpressionPartToHighlight(@NotNull GrExpression expr) {
    return expr instanceof GrClosableBlock ? ((GrClosableBlock)expr).getLBrace() : expr;
  }

  /**
   * checks only children of e
   */
  public static boolean hasErrorElements(@Nullable PsiElement e) {
    if (e == null) return false;

    for (PsiElement child = e.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof PsiErrorElement) return true;
    }
    return false;
  }

  public static boolean isOperatorWithSimpleTypes(GrBinaryExpression binary, GroovyResolveResult result) {
    if (result.getElement() != null && result.isApplicable()) {
      return false;
    }

    GrExpression left = binary.getLeftOperand();
    GrExpression right = binary.getRightOperand();

    PsiType ltype = left.getType();
    PsiType rtype = right != null ? right.getType() : null;

    return TypesUtil.isNumericType(ltype) && (rtype == null || TypesUtil.isNumericType(rtype));
  }

  @Nullable
  public static String getLValueVarName(@NotNull PsiElement highlight) {
    final PsiElement parent = highlight.getParent();
    if (parent instanceof GrVariable) {
      return ((GrVariable)parent).getName();
    }
    else if (highlight instanceof GrReferenceExpression &&
             parent instanceof GrAssignmentExpression &&
             ((GrAssignmentExpression)parent).getLValue() == highlight) {
      final PsiElement resolved = ((GrReferenceExpression)highlight).resolve();
      if (resolved instanceof GrVariable && PsiUtil.isLocalVariable(resolved)) {
        return ((GrVariable)resolved).getName();
      }
    }

    return null;
  }

  @Nullable
  public static List<GrExpression> getExpressionArgumentsOfCall(@NotNull GrArgumentList argumentList) {
    final ArrayList<GrExpression> args = new ArrayList<>();

    for (GroovyPsiElement arg : argumentList.getAllArguments()) {
      if (arg instanceof GrSpreadArgument) {
        GrExpression spreaded = ((GrSpreadArgument)arg).getArgument();
        if (spreaded instanceof GrListOrMap && !((GrListOrMap)spreaded).isMap()) {
          Collections.addAll(args, ((GrListOrMap)spreaded).getInitializers());
        }
        else {
          return null;
        }
      }
      else if (arg instanceof GrExpression) {
        args.add((GrExpression)arg);
      }
      else if (arg instanceof GrNamedArgument) {
        args.add(((GrNamedArgument)arg).getExpression());
      }
    }

    final PsiElement parent = argumentList.getParent();
    if (parent instanceof GrIndexProperty && PsiUtil.isLValue((GroovyPsiElement)parent)) {
      args.add(TypeInferenceHelper.getInitializerFor((GrExpression)parent));
    }
    else if (parent instanceof GrMethodCallExpression) {
      ContainerUtil.addAll(args, ((GrMethodCallExpression)parent).getClosureArguments());
    }
    return args;
  }
}
