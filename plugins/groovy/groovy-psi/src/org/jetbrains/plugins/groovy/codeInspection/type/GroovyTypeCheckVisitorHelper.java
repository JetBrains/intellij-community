/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
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
package org.jetbrains.plugins.groovy.codeInspection.type;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.assignment.CallInfo;
import org.jetbrains.plugins.groovy.codeInspection.assignment.ParameterCastFix;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.findUsages.LiteralConstructorReference;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GdkMethodUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.spock.SpockUtils;

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

  public static boolean isOnlyOneMapParam(GrExpression[] exprs) {
    if (!(exprs.length == 1)) return false;

    final GrExpression e = exprs[0];
    return TypesUtil.isAssignableByMethodCallConversion(
      TypesUtil.createTypeByFQClassName(CommonClassNames.JAVA_UTIL_MAP, e),
      e.getType(),
      e
    );
  }

  @NotNull
  public static PsiElement getExpressionPartToHighlight(@NotNull GrExpression expr) {
    if (expr instanceof GrClosableBlock) {
      final PsiElement highlightElement = ((GrClosableBlock)expr).getLBrace();
      assert highlightElement != null;
      return highlightElement;
    }
    return expr;
  }

  public static boolean checkSimpleArrayAccess(@NotNull CallInfo<? extends GrIndexProperty> info, @Nullable PsiType type, @NotNull PsiType[] types) {
    if (!(type instanceof PsiArrayType)) return false;

    if (PsiUtil.isLValue(info.getCall())) {
      if (types.length == 2 &&
          TypesUtil.isAssignable(PsiType.INT, types[0], info.getCall()) &&
          TypesUtil.isAssignable(((PsiArrayType)type).getComponentType(), types[1], info.getCall())) {
        return true;
      }
    }
    else {
      if (types.length == 1 && TypesUtil.isAssignable(PsiType.INT, types[0], info.getCall())) {
        return true;
      }
    }

    return false;
  }

  public static boolean typesAreEqual(@NotNull PsiType expected, @NotNull PsiType actual, @NotNull PsiElement context) {
    return TypesUtil.isAssignableByMethodCallConversion(expected, actual, context) &&
           TypesUtil.isAssignableByMethodCallConversion(actual, expected, context);
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
        closureSignature, argumentTypes, id, argumentList, 1
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
        fixes.add(new ParameterCastFix(error.first, error.second, args.get(error.first)));
      }
    }
    return fixes.toArray(new LocalQuickFix[fixes.size()]);
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

  public static boolean checkCategoryQualifier(@NotNull GrReferenceExpression place,
                                               @Nullable GrExpression qualifier,
                                               @NotNull PsiMethod gdkMethod,
                                               @Nullable PsiSubstitutor substitutor) {
    PsiClass categoryAnnotationOwner = inferCategoryAnnotationOwner(place, qualifier);

    if (categoryAnnotationOwner != null) {
      PsiClassType categoryType = GdkMethodUtil.getCategoryType(categoryAnnotationOwner);
      if (categoryType != null) {
        return GdkMethodUtil.isCategoryMethod(gdkMethod, categoryType, qualifier, substitutor);
      }
    }

    return false;
  }

  @Nullable
  public static PsiClass inferCategoryAnnotationOwner(@NotNull GrReferenceExpression place, @Nullable GrExpression qualifier) {
    if (qualifier == null) {
      GrMethod container = PsiTreeUtil.getParentOfType(place, GrMethod.class, true, GrMember.class);
      if (container != null &&
          !container.hasModifierProperty(PsiModifier.STATIC)) { //only instance methods can be qualified by category class
        return container.getContainingClass();
      }
    }
    else if (PsiUtil.isThisReference(qualifier)) {
      PsiElement resolved = ((GrReferenceExpression)qualifier).resolve();
      if (resolved instanceof PsiClass) {
        return (PsiClass)resolved;
      }
    }
    return null;
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

  static boolean isImplicitReturnStatement(@NotNull GrExpression expression) {
    GrControlFlowOwner flowOwner = ControlFlowUtils.findControlFlowOwner(expression);
    return flowOwner != null &&
        PsiUtil.isExpressionStatement(expression) &&
        ControlFlowUtils.isReturnValue(expression, flowOwner) &&
        !PsiUtil.isVoidMethodCall(expression);
  }
}
