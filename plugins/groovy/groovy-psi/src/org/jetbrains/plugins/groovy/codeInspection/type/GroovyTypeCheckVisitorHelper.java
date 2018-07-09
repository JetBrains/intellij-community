// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.type;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.assignment.ParameterCastFix;
import org.jetbrains.plugins.groovy.ext.spock.SpockUtils;
import org.jetbrains.plugins.groovy.findUsages.LiteralConstructorReference;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrSpreadArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GroovyTypeCheckVisitorHelper {
  
  @SuppressWarnings("unchecked") 
  private static final Function<PsiType, PsiType> id = (Function<PsiType, PsiType>)Function.ID;
  
  @Nullable
  @Contract("null -> null")
  protected static GrListOrMap getTupleInitializer(@Nullable GrExpression initializer) {
    if (initializer instanceof GrListOrMap &&
        initializer.getReference() instanceof LiteralConstructorReference &&
        ((LiteralConstructorReference)initializer.getReference()).getConstructedClassType() != null) {
      return (GrListOrMap)initializer;
    }
    else {
      return null;
    }
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

  public static boolean isSpockTimesOperator(GrBinaryExpression call) {
    if (call.getOperationTokenType() == GroovyTokenTypes.mSTAR && PsiUtil.isExpressionStatement(call)) {
      GrExpression operand = call.getLeftOperand();
      if (operand instanceof GrLiteral && TypesUtil.isNumericType(operand.getType())) {
        PsiClass aClass = PsiUtil.getContextClass(call);
        if (InheritanceUtil.isInheritor(aClass, false, SpockUtils.SPEC_CLASS_NAME)) {
          return true;
        }
      }
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

  @NotNull
  public static LocalQuickFix[] genCastFixes(@NotNull GrSignature signature,
                                             @NotNull PsiType[] argumentTypes,
                                             @Nullable GrArgumentList argumentList) {
    if (argumentList == null) return LocalQuickFix.EMPTY_ARRAY;
    final List<GrExpression> args = getExpressionArgumentsOfCall(argumentList);
    if (args == null) return LocalQuickFix.EMPTY_ARRAY;

    final List<Pair<Integer, PsiType>> allErrors = new ArrayList<>();
    final List<GrClosureSignature> signatures = GrClosureSignatureUtil.generateSimpleSignatures(signature);
    for (GrClosureSignature closureSignature : signatures) {
      final GrClosureSignatureUtil.MapResultWithError<PsiType> map = GrClosureSignatureUtil.mapSimpleSignatureWithErrors(
        closureSignature, argumentTypes, id, argumentList, 255
      );
      if (map != null) {
        final List<Pair<Integer, PsiType>> errors = map.getErrors();
        for (Pair<Integer, PsiType> error : errors) {
          if (!(error.first == 0 && PsiImplUtil.hasNamedArguments(argumentList))) {
            allErrors.add(error);
          }
        }
      }
    }

    final ArrayList<LocalQuickFix> fixes = new ArrayList<>();
    for (Pair<Integer, PsiType> error : allErrors) {
      if (args.size() > error.first && error.second != null) {
        PsiType type = PsiImplUtil.normalizeWildcardTypeByPosition(error.second, args.get(error.first));
        if (type != null) fixes.add(new ParameterCastFix(error.first, type));
      }
    }
    return fixes.toArray(LocalQuickFix.EMPTY_ARRAY);
  }

  @NotNull
  public static String buildArgTypesList(@NotNull PsiType[] argTypes) {
    StringBuilder builder = new StringBuilder();
    builder.append("(");
    for (int i = 0; i < argTypes.length; i++) {
      if (i > 0) {
        builder.append(", ");
      }
      PsiType argType = argTypes[i];
      builder.append(argType != null ? argType.getInternalCanonicalText() : "?");
    }
    builder.append(")");
    return builder.toString();
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
    final ArrayList<GrExpression> args = ContainerUtil.newArrayList();

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
