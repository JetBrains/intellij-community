// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.redundancy;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.ObjectUtils.tryCast;
import static com.siyeh.ig.callMatcher.CallMatcher.*;

public class RedundantCollectionOperationInspection extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {
  private static final CallMatcher TO_ARRAY =
    anyOf(
      instanceCall(CommonClassNames.JAVA_UTIL_COLLECTION, "toArray").parameterCount(0),
      instanceCall(CommonClassNames.JAVA_UTIL_COLLECTION, "toArray").parameterTypes("T[]"));
  private static final CallMatcher SUBLIST =
    instanceCall(CommonClassNames.JAVA_UTIL_LIST, "subList").parameterTypes("int", "int");
  private static final CallMatcher AS_LIST =
    staticCall(CommonClassNames.JAVA_UTIL_ARRAYS, "asList").parameterCount(1);
  private static final CallMatcher SINGLETON =
    anyOf(
      staticCall(CommonClassNames.JAVA_UTIL_COLLECTIONS, "singleton", "singletonList").parameterCount(1),
      staticCall(CommonClassNames.JAVA_UTIL_LIST, "of").parameterTypes("E"),
      staticCall(CommonClassNames.JAVA_UTIL_SET, "of").parameterTypes("E"));
  private static final CallMatcher CONTAINS_ALL =
    instanceCall(CommonClassNames.JAVA_UTIL_COLLECTION, "containsAll").parameterTypes(CommonClassNames.JAVA_UTIL_COLLECTION);
  private static final CallMatcher CONTAINS =
    instanceCall(CommonClassNames.JAVA_UTIL_COLLECTION, "contains").parameterTypes(CommonClassNames.JAVA_LANG_OBJECT);

  private static final CallMapper<RedundantCollectionOperationHandler> HANDLERS =
    new CallMapper<RedundantCollectionOperationHandler>()
      .register(TO_ARRAY, AsListToArrayHandler::handler)
      .register(CONTAINS_ALL, ContainsAllSingletonHandler::handler)
      .register(CONTAINS, SingletonContainsHandler::handler);

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel6OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        PsiElement nameElement = call.getMethodExpression().getReferenceNameElement();
        if (nameElement == null) return;
        RedundantCollectionOperationHandler handler = HANDLERS.mapFirst(call);
        if (handler == null) return;
        holder.registerProblem(nameElement, handler.getProblemName(), new RedundantCollectionOperationFix(handler));
      }
    };
  }

  interface RedundantCollectionOperationHandler {
    default String getProblemName() {
      return InspectionGadgetsBundle.message("expression.can.be.replaced.problem.descriptor", getReplacement());
    }

    String getReplacement();

    void performFix(@NotNull Project project, @NotNull PsiMethodCallExpression call);
  }

  private static class AsListToArrayHandler implements RedundantCollectionOperationHandler {
    private final String myReplacementMethod;
    @NotNull private final SmartPsiElementPointer<PsiExpression> myArrayPtr;
    private final SmartPsiElementPointer<PsiExpression> myFromPtr;
    private final SmartPsiElementPointer<PsiExpression> myToPtr;
    @NotNull private final String mySourceComponentType;
    @NotNull private final String myTargetComponentType;

    private AsListToArrayHandler(PsiExpression from,
                                 PsiExpression to,
                                 @NotNull PsiExpression array,
                                 @NotNull String sourceComponentType,
                                 @NotNull String targetComponentType) {
      SmartPointerManager manager = SmartPointerManager.getInstance(array.getProject());
      myArrayPtr = manager.createSmartPsiElementPointer(array);
      myFromPtr = from == null ? null : manager.createSmartPsiElementPointer(from);
      myToPtr = to == null ? null : manager.createSmartPsiElementPointer(to);
      mySourceComponentType = sourceComponentType;
      myTargetComponentType = targetComponentType;
      if (from == null && to == null) {
        myReplacementMethod = "clone()";
      }
      else if (ExpressionUtils.isZero(from)) {
        myReplacementMethod = "Arrays.copyOf";
      }
      else {
        myReplacementMethod = "Arrays.copyOfRange";
      }
    }

    @Override
    public String getProblemName() {
      return InspectionGadgetsBundle.message("inspection.redundant.collection.operation.problem.arraycopy");
    }

    @Override
    public String getReplacement() {
      return myReplacementMethod;
    }

    @Override
    public void performFix(@NotNull Project project, @NotNull PsiMethodCallExpression call) {
      PsiExpression array = myArrayPtr.getElement();
      if (array == null) return;
      PsiExpression from = myFromPtr == null ? null : myFromPtr.getElement();
      PsiExpression to = myToPtr == null ? null : myToPtr.getElement();
      if ((from == null) != (to == null)) return;
      CommentTracker ct = new CommentTracker();
      String replacement;
      String suffix = "";
      if (!mySourceComponentType.equals(myTargetComponentType)) {
        suffix = "," + myTargetComponentType + "[].class";
      }
      if (from == null) {
        replacement = ParenthesesUtils.getText(ct.markUnchanged(array), ParenthesesUtils.POSTFIX_PRECEDENCE) + ".clone()";
      }
      else if (ExpressionUtils.isZero(from)) {
        replacement = CommonClassNames.JAVA_UTIL_ARRAYS + ".copyOf(" + ct.text(array) + "," + ct.text(to) + suffix + ")";
      }
      else {
        replacement =
          CommonClassNames.JAVA_UTIL_ARRAYS + ".copyOfRange(" + ct.text(array) + "," + ct.text(from) + "," + ct.text(to) + suffix + ")";
      }
      ct.replaceAndRestoreComments(call, replacement);
    }

    public static RedundantCollectionOperationHandler handler(PsiMethodCallExpression call) {
      PsiExpression arg = ArrayUtil.getFirstElement(call.getArgumentList().getExpressions());
      PsiExpression arrayLength = null;
      String targetComponentType;
      if (arg != null) {
        if (!(arg instanceof PsiNewExpression)) return null;
        PsiJavaCodeReferenceElement classRef = ((PsiNewExpression)arg).getClassReference();
        if (classRef == null) return null;
        targetComponentType = classRef.getQualifiedName();
        PsiExpression[] dimensions = ((PsiNewExpression)arg).getArrayDimensions();
        if (dimensions.length != 1) return null;
        if (!ExpressionUtils.isZero(dimensions[0])) {
          arrayLength = dimensions[0];
        }
      }
      else {
        targetComponentType = CommonClassNames.JAVA_LANG_OBJECT;
      }
      PsiExpression from = null;
      PsiExpression to = null;
      PsiMethodCallExpression qualifier = MethodCallUtils.getQualifierMethodCall(call);
      if (SUBLIST.test(qualifier)) {
        PsiExpression[] subListArgs = qualifier.getArgumentList().getExpressions();
        from = subListArgs[0];
        to = subListArgs[1];
        qualifier = MethodCallUtils.getQualifierMethodCall(qualifier);
      }
      if (!AS_LIST.test(qualifier) || MethodCallUtils.isVarArgCall(qualifier)) return null;
      PsiExpression array = qualifier.getArgumentList().getExpressions()[0];
      PsiArrayType sourceArrayType = tryCast(array.getType(), PsiArrayType.class);
      if (sourceArrayType == null) return null;
      PsiClass componentClass = PsiUtil.resolveClassInClassTypeOnly(sourceArrayType.getComponentType());
      if (componentClass == null) return null;
      String sourceComponentType = componentClass.getQualifiedName();
      if (sourceComponentType == null) return null;
      if (from != null && to != null) {
        if (arrayLength != null && !ExpressionUtils.isDifference(from, to, arrayLength)) return null;
      }
      else {
        if (!sourceComponentType.equals(targetComponentType)) return null;
        if (arrayLength != null) {
          PsiExpression arrayFromLength = ExpressionUtils.getArrayFromLengthExpression(arrayLength);
          if (arrayFromLength == null || !PsiEquivalenceUtil.areElementsEquivalent(array, arrayFromLength)) return null;
        }
      }
      return new AsListToArrayHandler(from, to, array, sourceComponentType, targetComponentType);
    }
  }

  private static class ContainsAllSingletonHandler implements RedundantCollectionOperationHandler {
    @Override
    public String getReplacement() {
      return "contains";
    }

    @Override
    public void performFix(@NotNull Project project, @NotNull PsiMethodCallExpression call) {
      PsiExpression arg = ArrayUtil.getFirstElement(call.getArgumentList().getExpressions());
      if (arg == null) return;
      PsiMethodCallExpression singleton = tryCast(PsiUtil.skipParenthesizedExprDown(arg), PsiMethodCallExpression.class);
      if (singleton == null) return;
      PsiExpression singletonArg = ArrayUtil.getFirstElement(singleton.getArgumentList().getExpressions());
      if (singletonArg == null) return;
      ExpressionUtils.bindCallTo(call, "contains");
      CommentTracker ct = new CommentTracker();
      ct.replaceAndRestoreComments(arg, ct.markUnchanged(singletonArg));
    }

    public static RedundantCollectionOperationHandler handler(PsiMethodCallExpression call) {
      PsiExpression containsAllArg = call.getArgumentList().getExpressions()[0];
      PsiMethodCallExpression maybeSingleton = tryCast(PsiUtil.skipParenthesizedExprDown(containsAllArg), PsiMethodCallExpression.class);
      if (!SINGLETON.test(maybeSingleton)) return null;
      return new ContainsAllSingletonHandler();
    }
  }

  private static class SingletonContainsHandler implements RedundantCollectionOperationHandler {
    @Override
    public String getReplacement() {
      return "Objects.equals";
    }

    @Override
    public void performFix(@NotNull Project project, @NotNull PsiMethodCallExpression call) {
      PsiMethodCallExpression qualifierCall = MethodCallUtils.getQualifierMethodCall(call);
      if (qualifierCall == null) return;
      PsiExpression left = ArrayUtil.getFirstElement(qualifierCall.getArgumentList().getExpressions());
      PsiExpression right = ArrayUtil.getFirstElement(call.getArgumentList().getExpressions());
      if (left == null || right == null) return;
      CommentTracker ct = new CommentTracker();
      PsiElement element =
        ct.replaceAndRestoreComments(call, CommonClassNames.JAVA_UTIL_OBJECTS + ".equals(" + ct.text(left) + "," + ct.text(right) + ")");
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(element);
    }

    static RedundantCollectionOperationHandler handler(PsiMethodCallExpression call) {
      if(!PsiUtil.isLanguageLevel7OrHigher(call)) return null;
      PsiMethodCallExpression qualifierCall = MethodCallUtils.getQualifierMethodCall(call);
      if (!SINGLETON.test(qualifierCall)) return null;
      return new SingletonContainsHandler();
    }
  }

  private static class RedundantCollectionOperationFix implements LocalQuickFix {
    private final RedundantCollectionOperationHandler myHandler;

    public RedundantCollectionOperationFix(RedundantCollectionOperationHandler handler) {
      myHandler = handler;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("replace.with", myHandler.getReplacement());
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.redundant.collection.operation.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiMethodCallExpression.class);
      if (call == null) return;
      myHandler.performFix(project, call);
    }
  }
}
