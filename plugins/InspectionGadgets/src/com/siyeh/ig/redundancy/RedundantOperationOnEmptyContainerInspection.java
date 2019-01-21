// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.redundancy;

import com.intellij.codeInsight.daemon.impl.quickfix.DeleteElementFix;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.DfaFactType;
import com.intellij.codeInspection.dataFlow.SpecialField;
import com.intellij.codeInspection.dataFlow.SpecialFieldValue;
import com.intellij.codeInspection.dataFlow.value.DfaConstValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.CommonClassNames.*;
import static com.siyeh.ig.callMatcher.CallMatcher.*;

public class RedundantOperationOnEmptyContainerInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher ARRAY_METHODS = staticCall(
    JAVA_UTIL_ARRAYS, "binarySearch", "fill", "parallelPrefix", "parallelSort", "setAll", "sort", "spliterator", "stream");
  private static final CallMatcher COLLECTION_METHODS = anyOf(
    instanceCall(JAVA_LANG_ITERABLE, "iterator", "spliterator").parameterCount(0),
    instanceCall(JAVA_LANG_ITERABLE, "forEach").parameterTypes(JAVA_UTIL_FUNCTION_CONSUMER),
    instanceCall(JAVA_UTIL_COLLECTION, "remove").parameterTypes(JAVA_LANG_OBJECT),
    instanceCall(JAVA_UTIL_COLLECTION, "removeAll", "retainAll").parameterTypes(JAVA_UTIL_COLLECTION),
    instanceCall(JAVA_UTIL_COLLECTION, "stream", "parallelStream", "clear").parameterCount(0),
    instanceCall(JAVA_UTIL_COLLECTION, "removeIf").parameterTypes(JAVA_UTIL_FUNCTION_PREDICATE),
    instanceCall(JAVA_UTIL_LIST, "remove").parameterTypes("int"),
    instanceCall(JAVA_UTIL_LIST, "replaceAll").parameterTypes("java.util.function.UnaryOperator"),
    instanceCall(JAVA_UTIL_LIST, "sort").parameterTypes(JAVA_UTIL_COMPARATOR)
  );
  private static final CallMatcher MAP_METHODS = anyOf(
    instanceCall(JAVA_UTIL_MAP, "forEach").parameterTypes("java.util.function.BiConsumer"),
    instanceCall(JAVA_UTIL_MAP, "get", "remove").parameterTypes(JAVA_LANG_OBJECT),
    instanceCall(JAVA_UTIL_MAP, "remove", "replace").parameterCount(2),
    instanceCall(JAVA_UTIL_MAP, "replace").parameterCount(3),
    instanceCall(JAVA_UTIL_MAP, "replaceAll").parameterTypes(JAVA_UTIL_FUNCTION_BI_FUNCTION),
    instanceCall(JAVA_UTIL_MAP, "clear").parameterCount(0)
  );
  private static final CallMatcher COLLECTIONS_EMPTY = staticCall(JAVA_UTIL_COLLECTIONS, "emptyList", "emptySet");

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        PsiExpression container = null;
        if (ARRAY_METHODS.test(call)) {
          container = ArrayUtil.getFirstElement(call.getArgumentList().getExpressions());
        } else if (COLLECTION_METHODS.test(call) || MAP_METHODS.test(call)) {
          container = call.getMethodExpression().getQualifierExpression();
          if ("iterator".equals(call.getMethodExpression().getReferenceName()) && COLLECTIONS_EMPTY.matches(container)) {
            return;
          }
        }
        container = PsiUtil.skipParenthesizedExprDown(container);
        if (container != null) {
          String msg = getProblemMessage(container);
          if (msg == null) return;
          LocalQuickFix fix = null;
          if (ExpressionUtils.isVoidContext(call)) {
            fix = new DeleteElementFix(call, "Remove call");
          }
          holder.registerProblem(container, msg, fix);
        }
      }

      @Override
      public void visitForeachStatement(PsiForeachStatement statement) {
        PsiExpression value = PsiUtil.skipParenthesizedExprDown(statement.getIteratedValue());
        if (value == null) return;
        String msg = getProblemMessage(value);
        if (msg == null) return;
        holder.registerProblem(value, msg, new DeleteElementFix(statement, "Remove loop"));
      }

      @Nullable
      public String getProblemMessage(PsiExpression value) {
        SpecialField lengthField;
        PsiType type = value.getType();
        String message;
        if (type instanceof PsiArrayType) {
          lengthField = SpecialField.ARRAY_LENGTH;
          message = InspectionsBundle.message("inspection.redundant.operation.on.empty.array.message");
        } else if (InheritanceUtil.isInheritor(type, JAVA_UTIL_COLLECTION)) {
          lengthField = SpecialField.COLLECTION_SIZE;
          message = InspectionsBundle.message("inspection.redundant.operation.on.empty.collection.message");
        } else if (InheritanceUtil.isInheritor(type, JAVA_UTIL_MAP)) {
          lengthField = SpecialField.COLLECTION_SIZE;
          message = InspectionsBundle.message("inspection.redundant.operation.on.empty.map.message");
        } else {
          return null;
        }
        SpecialFieldValue fact = CommonDataflow.getExpressionFact(value, DfaFactType.SPECIAL_FIELD_VALUE);
        DfaValue length = lengthField.extract(fact);
        if (length instanceof DfaConstValue && Long.valueOf(0).equals(((DfaConstValue)length).getValue())) {
          return message;
        }
        return null;
      }
    };
  }
}
