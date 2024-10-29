// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.intelliLang.inject.groovy;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.plugins.intelliLang.util.ContextComputationProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArrayInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyCallReference;

import java.util.Arrays;
import java.util.Map;

public final class GrInjectionUtil {
  static int findParameterIndex(PsiElement arg, GrCall call) {
    final GroovyResolveResult result = call.advancedResolve();
    if (!(result.getElement() instanceof PsiMethod method)) return -1;

    final Map<GrExpression, Pair<PsiParameter, PsiType>> map = GrClosureSignatureUtil
      .mapArgumentsToParameters(result, call, false, false, call.getNamedArguments(), call.getExpressionArguments(), call.getClosureArguments());
    if (map == null) return -1;

    final PsiParameter parameter = map.get(arg).first;
    if (parameter == null) return -1;

    return method.getParameterList().getParameterIndex(parameter);
  }

  static @Nullable PsiMethod getMethodFromLeftShiftOperator(@NotNull GrBinaryExpression expression) {
    IElementType operator = expression.getOperator();
    if (operator != GroovyElementTypes.LEFT_SHIFT_SIGN) return null;

    GroovyCallReference reference = expression.getReference();
    if (reference == null) return null;

    PsiElement resolvedElement = reference.resolve();
    if (!(resolvedElement instanceof PsiMethod method)) return null;
    return method;
  }

  static @Nullable PsiParameter getSingleParameterFromMethod(@Nullable PsiMethod method) {
    if (method == null) return null;
    PsiParameterList parameterList = method.getParameterList();
    return ContainerUtil.getOnlyItem(Arrays.asList(parameterList.getParameters()));
  }

  public interface AnnotatedElementVisitor {
    boolean visitMethodParameter(@NotNull GrExpression expression, @NotNull GrCall psiCallExpression);
    boolean visitMethodReturnStatement(@NotNull GrReturnStatement parent, @NotNull PsiMethod method);
    boolean visitVariable(@NotNull PsiVariable variable);
    boolean visitAnnotationParameter(@NotNull GrAnnotationNameValuePair nameValuePair, @NotNull PsiAnnotation psiAnnotation);
    boolean visitReference(@NotNull GrReferenceExpression expression);
    boolean visitBinaryExpression(@NotNull GrBinaryExpression expression);
  }

  public static void visitAnnotatedElements(@Nullable PsiElement element, AnnotatedElementVisitor visitor) {
    if (element == null) return;
    for (PsiElement cur = ContextComputationProcessor.getTopLevelInjectionTarget(element); cur != null; cur = cur.getParent()) {
      if (!visitAnnotatedElementInner(cur, visitor)) return;
    }
  }

  private static boolean visitAnnotatedElementInner(PsiElement element, AnnotatedElementVisitor visitor) {
    final PsiElement parent = element.getParent();

    if (element instanceof GrReferenceExpression) {
      if (!visitor.visitReference((GrReferenceExpression)element)) return false;
    }
    else if (element instanceof GrAnnotationNameValuePair && parent != null && parent.getParent() instanceof PsiAnnotation) {
      return visitor.visitAnnotationParameter((GrAnnotationNameValuePair)element, (PsiAnnotation)parent.getParent());
    }

    if (parent instanceof GrAssignmentExpression p) {
      if (p.getRValue() == element || p.getOperationTokenType() == GroovyTokenTypes.mPLUS_ASSIGN) {
        final GrExpression left = p.getLValue();
        if (left instanceof GrReferenceExpression) {
          if (!visitor.visitReference((GrReferenceExpression)left)) return false;
        }
      }
    }
    else if (parent instanceof GrConditionalExpression && ((GrConditionalExpression)parent).getCondition() == element) {
      return false;
    }
    else if (parent instanceof GrReturnStatement) {
      final PsiElement m = PsiTreeUtil.getParentOfType(parent, PsiMethod.class, GrClosableBlock.class, GroovyFile.class);
      if (m instanceof PsiMethod) {
        if (!visitor.visitMethodReturnStatement((GrReturnStatement)parent, (PsiMethod)m)) return false;
      }
    }
    else if (parent instanceof PsiVariable) {
      return visitor.visitVariable((PsiVariable)parent);
    }
    else if (parent instanceof PsiModifierListOwner) {
      return false; // PsiClass/PsiClassInitializer/PsiCodeBlock
    }
    else if (parent instanceof GrAnnotationArrayInitializer || parent instanceof GrAnnotationNameValuePair) {
      return true;
    }
    else if (parent instanceof GrBinaryExpression binaryExpression) {
      return visitor.visitBinaryExpression(binaryExpression);
    }
    else if (parent instanceof GrArgumentList && parent.getParent() instanceof GrCall && element instanceof GrExpression) {
      return visitor.visitMethodParameter((GrExpression)element, (GrCall)parent.getParent());
    }
    return true;
  }
}
