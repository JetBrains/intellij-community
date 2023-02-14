// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

import static com.siyeh.ig.psiutils.ExpressionUtils.getCallForQualifier;

public final class StreamApiUtil {
  @Contract("null -> null")
  public static PsiType getStreamElementType(PsiType type) {
    return getStreamElementType(type, true);
  }

  @Contract("null, _ -> null")
  public static PsiType getStreamElementType(PsiType type, boolean variableType) {
    if(!(type instanceof PsiClassType)) return null;
    PsiClass aClass = ((PsiClassType)type).resolve();
    if(com.intellij.psi.util.InheritanceUtil.isInheritor(aClass, false, CommonClassNames.JAVA_UTIL_STREAM_INT_STREAM)) {
      return PsiTypes.intType();
    }
    if(com.intellij.psi.util.InheritanceUtil.isInheritor(aClass, false, CommonClassNames.JAVA_UTIL_STREAM_LONG_STREAM)) {
      return PsiTypes.longType();
    }
    if(com.intellij.psi.util.InheritanceUtil.isInheritor(aClass, false, CommonClassNames.JAVA_UTIL_STREAM_DOUBLE_STREAM)) {
      return PsiTypes.doubleType();
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
    if (!(expression instanceof PsiMethodCallExpression call)) return false;
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
      return type.equals(PsiTypes.intType()) || type.equals(PsiTypes.longType()) || type.equals(PsiTypes.doubleType());
    }
    return true;
  }

  /**
   * Returns a Stream API class name (Stream, LongStream, IntStream, DoubleStream) which corresponds to given element type,
   * or null if there's no corresponding Stream API class.
   *
   * @param type stream element type
   * @return a fully-qualified class name
   */
  @Contract("null -> null")
  @Nullable
  public static String getStreamClassForType(@Nullable PsiType type) {
    if(type == null) return null;
    if(type instanceof PsiPrimitiveType) {
      if(type.equals(PsiTypes.intType())) return CommonClassNames.JAVA_UTIL_STREAM_INT_STREAM;
      if(type.equals(PsiTypes.longType())) return CommonClassNames.JAVA_UTIL_STREAM_LONG_STREAM;
      if(type.equals(PsiTypes.doubleType())) return CommonClassNames.JAVA_UTIL_STREAM_DOUBLE_STREAM;
      return null;
    }
    return CommonClassNames.JAVA_UTIL_STREAM_STREAM;
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
                                                           Predicate<? super String> isWantedCall,
                                                           Predicate<? super String> isAllowedIntermediateCall) {
    return findSubsequentCall(call, isWantedCall, c -> false, isAllowedIntermediateCall);
  }

  /**
   * Returns call from call chain which name satisfies isWantedCall predicate or is a collect() call accepting a collector
   * which satisfies the isWantedCollector call. Also checks that all calls between start call and wanted call
   * satisfies isAllowedIntermediateCall.
   *
   * @param call call chain
   * @param isWantedCall predicate on the name of wanted call
   * @param isWantedCollector predicate on the wanted collector call
   * @param isAllowedIntermediateCall predicate on the name of any other call between start call and wanted call
   * @return call that satisfies isWantedCall predicate or null otherwise
   */
  @Nullable
  public static PsiMethodCallExpression findSubsequentCall(PsiMethodCallExpression call,
                                                           Predicate<? super String> isWantedCall,
                                                           Predicate<? super PsiMethodCallExpression> isWantedCollector,
                                                           Predicate<? super String> isAllowedIntermediateCall) {
    for (PsiMethodCallExpression chainCall = getCallForQualifier(call); chainCall != null;
         chainCall = getCallForQualifier(chainCall)) {
      String name = chainCall.getMethodExpression().getReferenceName();
      if (name == null) return null;
      if (isWantedCall.test(name)) return chainCall;
      if (name.equals("collect")) {
        PsiExpression[] args = chainCall.getArgumentList().getExpressions();
        if (args.length == 1) {
          PsiMethodCallExpression collectorCall =
            ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(args[0]), PsiMethodCallExpression.class);
          if (collectorCall != null && isWantedCollector.test(collectorCall)) {
            return collectorCall;
          }
        }
      }
      if (!isAllowedIntermediateCall.test(name) ||
          !InheritanceUtil.isInheritor(chainCall.getType(), CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM)) {
        return null;
      }
    }
    return null;
  }
}
