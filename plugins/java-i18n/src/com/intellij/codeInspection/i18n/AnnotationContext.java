// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.i18n;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Represents a context where annotations should be searched to augment expression type.
 * The context consists of PsiType and PsiModifierListOwner (either could be null).
 */
final class AnnotationContext {
  private static final AnnotationContext EMPTY = new AnnotationContext(null, null);

  private final @Nullable PsiModifierListOwner myOwner;
  private final @Nullable PsiType myType;
  private final @Nullable Supplier<Stream<PsiModifierListOwner>> myNext;

  private AnnotationContext(@Nullable PsiModifierListOwner owner, @Nullable PsiType type) {
    this(owner, type, null);
  }

  private AnnotationContext(@Nullable PsiModifierListOwner owner,
                            @Nullable PsiType type,
                            @Nullable Supplier<Stream<PsiModifierListOwner>> next) {
    myOwner = owner;
    myType = type;
    myNext = next;
  }

  AnnotationContext withType(PsiType type) {
    return new AnnotationContext(myOwner, type, myNext);
  }

  public @Nullable PsiModifierListOwner getOwner() {
    return myOwner;
  }

  public @NotNull Stream<PsiModifierListOwner> secondaryItems() {
    return myNext == null ? Stream.empty() : myNext.get();
  }
  
  public @NotNull Stream<PsiAnnotationOwner> allItems() {
    return StreamEx.<PsiAnnotationOwner>ofNullable(myType)
      .append(StreamEx.ofNullable(myOwner == null ? null : myOwner.getModifierList()))
      .append(secondaryItems().map(PsiModifierListOwner::getModifierList));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    AnnotationContext context = (AnnotationContext)o;
    return Objects.equals(myOwner, context.myOwner) &&
           Objects.equals(myType, context.myType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myOwner, myType);
  }

  public @Nullable PsiType getType() {
    return myType;
  }

  static @NotNull AnnotationContext fromModifierListOwner(@Nullable PsiModifierListOwner owner) {
    if (owner instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)owner;
      return new AnnotationContext(owner, (method).getReturnType(), () -> {
        HashSet<PsiMethod> visited = new HashSet<>();
        return StreamEx.ofNullable(getKotlinProperty(owner))
          .append(StreamEx.ofTree(method, m -> StreamEx.of(m.findSuperMethods()).filter(visited::add)).skip(1));
      });
    }
    if (owner instanceof PsiParameter) {
      PsiParameter parameter = (PsiParameter)owner;
      PsiMethod method = ObjectUtils.tryCast(parameter.getDeclarationScope(), PsiMethod.class);
      if (method != null) {
        int index = method.getParameterList().getParameterIndex(parameter);
        if (index >= 0) {
          Supplier<Stream<PsiModifierListOwner>> supplier = () -> {
            HashSet<PsiMethod> visited = new HashSet<>();
            return StreamEx.ofTree(method, m -> StreamEx.of(m.findSuperMethods()).filter(visited::add)).skip(1)
              .map(m -> m.getParameterList().getParameter(index)).select(PsiModifierListOwner.class);
          };
          return new AnnotationContext(owner, ((PsiVariable)owner).getType(), supplier);
        }
      }
    }
    if (owner instanceof PsiVariable) {
      return new AnnotationContext(owner, ((PsiVariable)owner).getType());
    }
    return EMPTY;
  }

  static @NotNull AnnotationContext fromExpression(@NotNull UExpression expression) {
    AnnotationContext context = fromMethodReturn(expression);
    if (context != EMPTY) return context;
    context = fromArgument(expression);
    if (context != EMPTY) return context;
    return fromInitializer(expression);
  }

  private static @NotNull AnnotationContext fromMethodReturn(@NotNull UExpression expression) {
    PsiMethod method;
    PsiType returnType = null;
    PsiModifierListOwner next = null;
    UElement parent = expression.getUastParent();
    if (parent instanceof UCallExpression) {
      // Nested annotations in Kotlin
      UAnnotation anno = UastContextKt.toUElement(parent.getSourcePsi(), UAnnotation.class);
      if (anno != null) {
        parent = anno;
      }
    }
    if (parent instanceof UAnnotationMethod) {
      UExpression defaultValue = ((UAnnotationMethod)parent).getUastDefaultValue();
      if (defaultValue == null || !expressionsAreEquivalent(defaultValue, expression)) return EMPTY;
      method = ((UAnnotationMethod)parent).getPsi();
    }
    else if (parent instanceof UNamedExpression) {
      method = UastUtils.getAnnotationMethod((UNamedExpression)parent);
    }
    else if (parent instanceof UAnnotation) {
      PsiClass psiClass = ((UAnnotation)parent).resolve();
      if (psiClass == null || !psiClass.isAnnotationType()) return EMPTY;
      method = ArrayUtil.getFirstElement(psiClass.findMethodsByName("value", false));
    }
    else {
      UElement jumpTarget = null;
      if (parent instanceof UReturnExpression) {
        jumpTarget = ((UReturnExpression)parent).getJumpTarget();
      }
      else if (parent instanceof ULambdaExpression && expression instanceof UBlockExpression) {
        jumpTarget = parent;
      }
      if (jumpTarget instanceof UMethod) {
        method = ((UMethod)jumpTarget).getJavaPsi();
      }
      else if (jumpTarget instanceof ULambdaExpression) {
        next = getFunctionalParameter((ULambdaExpression)jumpTarget);
        PsiType type = ((ULambdaExpression)jumpTarget).getFunctionalInterfaceType();
        returnType = LambdaUtil.getFunctionalInterfaceReturnType(type);
        if (type == null) return fromModifierListOwner(next);
        method = LambdaUtil.getFunctionalInterfaceMethod(type);
      }
      else {
        return EMPTY;
      }
    }
    if (method == null) return EMPTY;
    AnnotationContext result = fromModifierListOwner(method).withType(returnType);
    if (next != null) {
      PsiModifierListOwner finalNext = next;
      return new AnnotationContext(result.myOwner, result.myType, () -> StreamEx.of(finalNext).append(result.secondaryItems()));
    }
    return result;
  }

  private static @Nullable PsiModifierListOwner getKotlinProperty(@NotNull PsiModifierListOwner owner) {
    if (!(owner instanceof PsiMethod)) return null;
    // Looks ugly but without this check, owner.getNavigationElement() may load PSI or even call decompiler
    if (!owner.getClass().getSimpleName().equals("KtUltraLightMethodForSourceDeclaration")) return null;
    // If assignment target is Kotlin property, it resolves to the getter but annotation will be applied to the field
    // (unless @get:Nls is used), so we have to navigate to the corresponding field.
    UElement element = UastContextKt.toUElement(owner.getNavigationElement());
    if (element instanceof UField) {
      PsiElement javaPsi = element.getJavaPsi();
      if (javaPsi instanceof PsiField) {
        return (PsiField)javaPsi;
      }
    }
    return null;
  }

  // Annotation on functional parameter is considered to be a return type annotation
  // (at least until type annotations will be supported in Kotlin)
  private static @Nullable PsiParameter getFunctionalParameter(ULambdaExpression function) {
    UCallExpression call = ObjectUtils.tryCast(function.getUastParent(), UCallExpression.class);
    if (call != null) {
      PsiMethod calledMethod = call.resolve();
      if (calledMethod != null) {
        return getParameter(calledMethod, call, function);
      }
    }
    return null;
  }

  private static @NotNull AnnotationContext fromArgument(@NotNull UExpression expression) {
    UElement parent = expression.getUastParent();
    UCallExpression callExpression = UastUtils.getUCallExpression(parent, 1);
    if (callExpression == null) return EMPTY;

    PsiMethod method = callExpression.resolve();
    if (method == null) return EMPTY;
    PsiParameter parameter = getParameter(method, callExpression, expression);
    if (parameter == null) return EMPTY;
    PsiType parameterType = parameter.getType();
    PsiElement psi = callExpression.getSourcePsi();
    if (psi instanceof PsiMethodCallExpression) {
      PsiSubstitutor substitutor = ((PsiMethodCallExpression)psi).getMethodExpression().advancedResolve(false).getSubstitutor();
      parameterType = substitutor.substitute(parameterType);
    }
    return fromModifierListOwner(parameter).withType(parameterType);
  }

  @NotNull
  private static AnnotationContext fromInitializer(UExpression expression) {
    UElement parent = expression.getUastParent();
    PsiModifierListOwner var = null;

    if (parent instanceof UVariable) {
      var = ObjectUtils.tryCast(parent.getJavaPsi(), PsiModifierListOwner.class);
    }
    else if (parent instanceof UBinaryExpression) {
      UBinaryExpression binOp = (UBinaryExpression)parent;
      UastBinaryOperator operator = binOp.getOperator();
      UExpression rightOperand = binOp.getRightOperand();
      if ((operator == UastBinaryOperator.ASSIGN || operator == UastBinaryOperator.PLUS_ASSIGN) &&
          expressionsAreEquivalent(expression, rightOperand)) {
        UExpression leftOperand = UastUtils.skipParenthesizedExprDown(binOp.getLeftOperand());
        UReferenceExpression lValue = ObjectUtils.tryCast(leftOperand, UReferenceExpression.class);
        if (lValue instanceof UQualifiedReferenceExpression) {
          lValue = ObjectUtils.tryCast(((UQualifiedReferenceExpression)lValue).getSelector(), UReferenceExpression.class);
        }
        if (lValue != null) {
          var = ObjectUtils.tryCast(lValue.resolve(), PsiModifierListOwner.class);
        }
        else {
          while (leftOperand instanceof UArrayAccessExpression) {
            leftOperand = ((UArrayAccessExpression)leftOperand).getReceiver();
          }
          if (leftOperand instanceof UResolvable) {
            var = ObjectUtils.tryCast(((UResolvable)leftOperand).resolve(), PsiModifierListOwner.class);
          }
        }
        if (var != null &&
            var.getLanguage().isKindOf(JavaLanguage.INSTANCE) &&
            var instanceof PsiMethod &&
            PsiType.VOID.equals(((PsiMethod)var).getReturnType())) {
          // If assignment target is Java, it resolves to the setter
          PsiParameter[] parameters = ((PsiMethod)var).getParameterList().getParameters();
          if (parameters.length == 1) {
            var = parameters[0];
          }
        }
      }
    }
    else if (parent instanceof USwitchClauseExpression) {
      if (((USwitchClauseExpression)parent).getCaseValues().contains(normalize(expression))) {
        USwitchExpression switchExpression = UastUtils.getParentOfType(parent, USwitchExpression.class);
        if (switchExpression != null) {
          UExpression selector = switchExpression.getExpression();
          if (selector instanceof UResolvable) {
            var = ObjectUtils.tryCast(((UResolvable)selector).resolve(), PsiModifierListOwner.class);
          }
        }
      }
    }
    return fromModifierListOwner(var);
  }

  /**
   * @return true if expressions are the same after normalization
   */
  static boolean expressionsAreEquivalent(@NotNull UExpression expr1, @NotNull UExpression expr2) {
    return normalize(expr1).equals(normalize(expr2));
  }

  /**
   * @param expression expression to normalize
   * @return normalized expression
   */
  static @NotNull UExpression normalize(@NotNull UExpression expression) {
    if (expression instanceof UPolyadicExpression && ((UPolyadicExpression)expression).getOperator() == UastBinaryOperator.PLUS) {
      List<UExpression> operands = ((UPolyadicExpression)expression).getOperands();
      if (operands.size() == 1) {
        return operands.get(0);
      }
    }
    return expression;
  }

  /**
   * @param method resolved method
   * @param call call
   * @param arg argument
   * @return parameter that corresponds to a given argument of a given call
   */
  static @Nullable PsiParameter getParameter(PsiMethod method, UCallExpression call, UExpression arg) {
    final PsiParameter[] params = method.getParameterList().getParameters();
    while (true) {
      UElement parent = arg.getUastParent();
      if (call.equals(parent)) break;
      if (!(parent instanceof UExpression)) return null;
      arg = (UExpression)parent;
    }
    arg = normalize(arg);
    for (int i = 0; i < params.length; i++) {
      UExpression argument = call.getArgumentForParameter(i);
      if (arg.equals(argument) ||
          (argument instanceof UExpressionList &&
           ((UExpressionList)argument).getKind() == UastSpecialExpressionKind.VARARGS &&
           ((UExpressionList)argument).getExpressions().contains(arg))) {
        return params[i];
      }
    }
    return null;
  }
}
