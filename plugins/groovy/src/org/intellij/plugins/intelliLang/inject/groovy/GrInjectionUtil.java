/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.intellij.plugins.intelliLang.inject.groovy;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.intellij.plugins.intelliLang.util.ContextComputationProcessor;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArrayInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;

import java.util.Map;

/**
 * Created by Max Medvedev on 9/12/13
 */
public class GrInjectionUtil {
  static int findParameterIndex(PsiElement arg, GrCall call) {
    final GroovyResolveResult result = call.advancedResolve();
    if (!(result.getElement() instanceof PsiMethod)) return -1;

    final Map<GrExpression, Pair<PsiParameter, PsiType>> map = GrClosureSignatureUtil
      .mapArgumentsToParameters(result, call, false, false, call.getNamedArguments(), call.getExpressionArguments(), call.getClosureArguments());
    if (map == null) return -1;

    final PsiMethod method = (PsiMethod)result.getElement();
    final PsiParameter parameter = map.get(arg).first;
    if (parameter == null) return -1;

    return method.getParameterList().getParameterIndex(parameter);
  }

  public interface AnnotatedElementVisitor {
    boolean visitMethodParameter(GrExpression expression, GrCall psiCallExpression);
    boolean visitMethodReturnStatement(GrReturnStatement parent, PsiMethod method);
    boolean visitVariable(PsiVariable variable);
    boolean visitAnnotationParameter(GrAnnotationNameValuePair nameValuePair, PsiAnnotation psiAnnotation);
    boolean visitReference(GrReferenceExpression expression);
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

    if (parent instanceof GrAssignmentExpression) {
      final GrAssignmentExpression p = (GrAssignmentExpression)parent;
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
    else if (parent instanceof GrArgumentList && parent.getParent() instanceof GrCall && element instanceof GrExpression) {
      return visitor.visitMethodParameter((GrExpression)element, (GrCall)parent.getParent());
    }
    return true;
  }
}
