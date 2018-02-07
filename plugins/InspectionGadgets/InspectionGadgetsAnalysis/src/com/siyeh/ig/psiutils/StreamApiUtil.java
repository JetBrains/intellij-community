/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

import static com.siyeh.ig.psiutils.ExpressionUtils.getCallForQualifier;

/**
 * @author Tagir Valeev
 */
public class StreamApiUtil {
  @Contract("null -> null")
  public static PsiType getStreamElementType(PsiType type) {
    return getStreamElementType(type, true);
  }

  @Contract("null, _ -> null")
  public static PsiType getStreamElementType(PsiType type, boolean variableType) {
    if(!(type instanceof PsiClassType)) return null;
    PsiClass aClass = ((PsiClassType)type).resolve();
    if(com.intellij.psi.util.InheritanceUtil.isInheritor(aClass, false, CommonClassNames.JAVA_UTIL_STREAM_INT_STREAM)) {
      return PsiType.INT;
    }
    if(com.intellij.psi.util.InheritanceUtil.isInheritor(aClass, false, CommonClassNames.JAVA_UTIL_STREAM_LONG_STREAM)) {
      return PsiType.LONG;
    }
    if(com.intellij.psi.util.InheritanceUtil.isInheritor(aClass, false, CommonClassNames.JAVA_UTIL_STREAM_DOUBLE_STREAM)) {
      return PsiType.DOUBLE;
    }
    if(!com.intellij.psi.util.InheritanceUtil.isInheritor(aClass, false, CommonClassNames.JAVA_UTIL_STREAM_STREAM)) {
      return null;
    }
    PsiType streamType = PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_UTIL_STREAM_STREAM, 0, false);
    if (variableType) {
      if (streamType instanceof PsiIntersectionType) {
        return null;
      }
      streamType = GenericsUtil.getVariableTypeByExpressionType(streamType);
    }
    return streamType;
  }

  public static boolean isNullOrEmptyStream(PsiExpression expression) {
    if(ExpressionUtils.isNullLiteral(expression)) {
      return true;
    }
    if (!(expression instanceof PsiMethodCallExpression)) return false;
    PsiMethodCallExpression call = (PsiMethodCallExpression)expression;
    String name = call.getMethodExpression().getReferenceName();
    if ((!"empty".equals(name) && !"of".equals(name)) || !call.getArgumentList().isEmpty()) {
      return false;
    }
    PsiMethod method = call.resolveMethod();
    if (method == null || !method.hasModifierProperty(PsiModifier.STATIC)) return false;
    PsiClass aClass = method.getContainingClass();
    if(aClass == null) return false;
    String qualifiedName = aClass.getQualifiedName();
    return qualifiedName != null && qualifiedName.startsWith("java.util.stream.");
  }

  @Contract("null -> false")
  public static boolean isSupportedStreamElement(PsiType type) {
    if(type == null) return false;
    if(type instanceof PsiPrimitiveType) {
      return type.equals(PsiType.INT) || type.equals(PsiType.LONG) || type.equals(PsiType.DOUBLE);
    }
    return true;
  }

  /**
   * Returns call from call chain which name satisfies isWantedCall predicate.
   * Also checks that all calls between start call and wanted call satisfies isAllowedIntermediateCall
   * @param call call chain
   * @param isWantedCall predicate on the name of wanted call
   * @param isAllowedIntermediateCall predicate on the name of any other call between start call and wanted call
   * @return call that satisfies isWantedCall predicate or null otherwise
   */
  @Nullable
  public static PsiMethodCallExpression findSubsequentCall(PsiMethodCallExpression call,
                                                           Predicate<String> isWantedCall,
                                                           Predicate<String> isAllowedIntermediateCall) {
    for (PsiMethodCallExpression chainCall = getCallForQualifier(call); chainCall != null;
         chainCall = getCallForQualifier(chainCall)) {
      String name = chainCall.getMethodExpression().getReferenceName();
      if (name == null) return null;
      if (isWantedCall.test(name)) return chainCall;
      if (!isAllowedIntermediateCall.test(name) ||
          !InheritanceUtil.isInheritor(chainCall.getType(), CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM)) {
        return null;
      }
    }
    return null;
  }
}
