// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.impl.UnusedSymbolUtil;
import com.intellij.codeInspection.dataFlow.Mutability;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CollectionUtils;
import com.siyeh.ig.psiutils.ConstructionUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.SideEffectChecker;
import com.siyeh.ig.ui.ExternalizableStringSet;
import one.util.streamex.StreamEx;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.siyeh.ig.psiutils.ClassUtils.isImmutable;

public class MismatchedCollectionQueryUpdateInspectionBase extends BaseInspection {
  private static final CallMatcher TRANSFORMED = CallMatcher.staticCall(
    CommonClassNames.JAVA_UTIL_COLLECTIONS, "asLifoQueue", "checkedCollection", "checkedList", "checkedMap", "checkedNavigableMap",
    "checkedNavigableSet", "checkedQueue", "checkedSet", "checkedSortedMap", "checkedSortedSet", "newSetFromMap", "synchronizedCollection",
    "synchronizedList", "synchronizedMap", "synchronizedNavigableMap", "synchronizedNavigableSet", "synchronizedSet",
    "synchronizedSortedMap", "synchronizedSortedSet");
  private static final CallMatcher DERIVED = CallMatcher.anyOf(
    CollectionUtils.DERIVED_COLLECTION,
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_LIST, "subList"),
    CallMatcher.instanceCall("java.util.SortedMap", "headMap", "tailMap", "subMap"),
    CallMatcher.instanceCall("java.util.SortedSet", "headSet", "tailSet", "subSet"));

  private static final CallMatcher COLLECTION_SAFE_ARGUMENT_METHODS =
    CallMatcher.anyOf(
      CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_COLLECTION, "addAll", "removeAll", "containsAll", "remove"),
      CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_MAP, "putAll", "remove")
    );

  private static final Set<String> COLLECTIONS_QUERIES =
    ContainerUtil.set("binarySearch", "disjoint", "indexOfSubList", "lastIndexOfSubList", "max", "min");

  private static final Set<String> COLLECTIONS_UPDATES = ContainerUtil.set("addAll", "fill", "copy", "replaceAll", "sort");

  private static final Set<String> COLLECTIONS_ALL =
    StreamEx.of(COLLECTIONS_QUERIES).append(COLLECTIONS_UPDATES).toImmutableSet();

  @SuppressWarnings("PublicField")
  public final ExternalizableStringSet queryNames =
    new ExternalizableStringSet(
      "contains", "copyInto", "equals", "forEach", "get", "hashCode", "iterator", "parallelStream", "propertyNames",
      "replaceAll", "save", "size", "store", "stream", "toArray", "toString", "write");
  @SuppressWarnings("PublicField")
  public final ExternalizableStringSet updateNames =
    new ExternalizableStringSet("add", "clear", "insert", "load", "merge", "offer", "poll", "pop", "push", "put", "remove", "replace",
                                "retain", "set", "take");

  @SuppressWarnings("PublicField")
  public final ExternalizableStringSet ignoredClasses = new ExternalizableStringSet();

  static boolean isEmptyCollectionInitializer(PsiExpression initializer) {
    if (!(initializer instanceof PsiNewExpression)) {
      return ConstructionUtils.isEmptyCollectionInitializer(initializer);
    }
    final PsiNewExpression newExpression = (PsiNewExpression)initializer;
    final PsiExpressionList argumentList = newExpression.getArgumentList();
    if (argumentList == null) {
      return false;
    }
    final PsiExpression[] arguments = argumentList.getExpressions();
    for (final PsiExpression argument : arguments) {
      final PsiType argumentType = argument.getType();
      if (argumentType == null) {
        return false;
      }
      if (CollectionUtils.isCollectionClassOrInterface(argumentType)) {
        return false;
      }
      if (argumentType instanceof PsiArrayType) {
        return false;
      }
    }
    return true;
  }

  static boolean isCollectionInitializer(PsiExpression initializer) {
    return isEmptyCollectionInitializer(initializer) || ConstructionUtils.isPrepopulatedCollectionInitializer(initializer);
  }

  @Pattern(VALID_ID_PATTERN)
  @Override
  @NotNull
  public String getID() {
    return "MismatchedQueryAndUpdateOfCollection";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("mismatched.update.collection.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final boolean updated = ((Boolean)infos[0]).booleanValue();
    if (updated) {
      return InspectionGadgetsBundle.message("mismatched.update.collection.problem.descriptor.updated.not.queried");
    }
    else {
      return InspectionGadgetsBundle.message("mismatched.update.collection.problem.description.queried.not.updated");
    }
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public boolean runForWholeFile() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MismatchedCollectionQueryUpdateVisitor();
  }

  private class MismatchedCollectionQueryUpdateVisitor extends BaseInspectionVisitor {
    private void register(PsiVariable variable, boolean written) {
      if (written) {
        PsiExpression initializer = variable.getInitializer();
        if (initializer != null) {
          List<PsiExpression> expressions = ExpressionUtils.nonStructuralChildren(initializer).collect(Collectors.toList());
          if (!expressions.stream().allMatch(MismatchedCollectionQueryUpdateInspectionBase::isCollectionInitializer)) {
            expressions.stream().filter(MismatchedCollectionQueryUpdateInspectionBase::isCollectionInitializer)
                       .forEach(emptyCollection -> registerError(emptyCollection, Boolean.TRUE));
            return;
          }
        }
      }
      registerVariableError(variable, written);
    }

    @Override
    public void visitField(@NotNull PsiField field) {
      super.visitField(field);
      if (!field.hasModifierProperty(PsiModifier.PRIVATE)) {
        PsiClass aClass = field.getContainingClass();
        if (aClass == null || !aClass.hasModifierProperty(PsiModifier.PRIVATE) || field.hasModifierProperty(PsiModifier.PUBLIC)) {
          // Public field within private class can be written/read via reflection even without setAccessible hacks
          // so we don't analyze such fields to reduce false-positives
          return;
        }
      }
      final PsiClass containingClass = PsiUtil.getTopLevelClass(field);
      if (!checkVariable(field, containingClass)) {
        return;
      }
      QueryUpdateInfo info = getCollectionQueryUpdateInfo(field, containingClass);
      final boolean written = info.updated || updatedViaInitializer(field);
      final boolean read = info.queried || queriedViaInitializer(field);
      if (read == written || UnusedSymbolUtil.isImplicitWrite(field) || UnusedSymbolUtil.isImplicitRead(field)) {
        // Even implicit read of the mutable collection field may cause collection change
        return;
      }
      register(field, written);
    }

    @Override
    public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
      super.visitLocalVariable(variable);
      final PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
      if (!checkVariable(variable, codeBlock)) {
        return;
      }
      QueryUpdateInfo info = getCollectionQueryUpdateInfo(variable, codeBlock);
      final boolean written = info.updated || updatedViaInitializer(variable);
      final boolean read = info.queried || queriedViaInitializer(variable);
      if (read != written) {
        register(variable, written);
      }
    }

    private boolean checkVariable(PsiVariable variable, PsiElement context) {
      if (context == null) {
        return false;
      }
      final PsiType type = variable.getType();
      if (!CollectionUtils.isCollectionClassOrInterface(type)) {
        return false;
      }
      return ignoredClasses.stream().noneMatch(className -> InheritanceUtil.isInheritor(type, className));
    }

    private boolean updatedViaInitializer(PsiVariable variable) {
      final PsiExpression initializer = variable.getInitializer();
      if (initializer != null &&
          !ExpressionUtils.nonStructuralChildren(initializer)
                          .allMatch(MismatchedCollectionQueryUpdateInspectionBase::isEmptyCollectionInitializer)) {
        return true;
      }
      if (initializer instanceof PsiNewExpression) {
        final PsiNewExpression newExpression = (PsiNewExpression)initializer;
        final PsiAnonymousClass anonymousClass = newExpression.getAnonymousClass();
        if (anonymousClass != null) {
          if (getCollectionQueryUpdateInfo(null, anonymousClass).updated) {
            return true;
          }
          final ThisPassedAsArgumentVisitor visitor = new ThisPassedAsArgumentVisitor();
          anonymousClass.accept(visitor);
          if (visitor.isPassed()) {
            return true;
          }
        }
      }
      return false;
    }

    private boolean queriedViaInitializer(PsiVariable variable) {
      final PsiExpression initializer = variable.getInitializer();
      return initializer != null &&
             ExpressionUtils.nonStructuralChildren(initializer)
                            .noneMatch(MismatchedCollectionQueryUpdateInspectionBase::isCollectionInitializer);
    }
  }

  private QueryUpdateInfo getCollectionQueryUpdateInfo(@Nullable PsiVariable variable, PsiElement context) {
    QueryUpdateInfo info = new QueryUpdateInfo();
    class Visitor extends JavaRecursiveElementWalkingVisitor {
      @Override
      public void visitReferenceExpression(PsiReferenceExpression ref) {
        super.visitReferenceExpression(ref);
        if (variable == null) {
          if (ref.getQualifierExpression() == null) {
            makeUpdated();
            makeQueried();
          }
        } else if (ref.isReferenceTo(variable)) {
          process(findEffectiveReference(ref));
        }
      }

      @Override
      public void visitThisExpression(PsiThisExpression expression) {
        super.visitThisExpression(expression);
        if (variable == null) {
          process(findEffectiveReference(expression));
        }
      }

      private void makeUpdated() {
        info.updated = true;
        if (info.queried) {
          stopWalking();
        }
      }

      private void makeQueried() {
        info.queried = true;
        if (info.updated) {
          stopWalking();
        }
      }

      private void process(PsiExpression reference) {
        PsiMethodCallExpression qualifiedCall = ExpressionUtils.getCallForQualifier(reference);
        if (qualifiedCall != null) {
          processQualifiedCall(qualifiedCall);
          return;
        }
        PsiElement parent = reference.getParent();
        if (parent instanceof PsiExpressionList) {
          PsiExpressionList args = (PsiExpressionList)parent;
          PsiCallExpression surroundingCall = ObjectUtils.tryCast(args.getParent(), PsiCallExpression.class);
          if (surroundingCall != null) {
            if (surroundingCall instanceof PsiMethodCallExpression &&
                processCollectionMethods((PsiMethodCallExpression)surroundingCall, reference)) {
              return;
            }
            makeQueried();
            if (!isQueryMethod(surroundingCall) && !COLLECTION_SAFE_ARGUMENT_METHODS.matches(surroundingCall)) {
              makeUpdated();
            }
            return;
          }
        }
        if (parent instanceof PsiMethodReferenceExpression) {
          processQualifiedMethodReference(((PsiMethodReferenceExpression)parent));
          return;
        }
        if (parent instanceof PsiForeachStatement && ((PsiForeachStatement)parent).getIteratedValue() == reference) {
          makeQueried();
          return;
        }
        if (parent instanceof PsiAssignmentExpression && ((PsiAssignmentExpression)parent).getLExpression() == reference) {
          PsiExpression rValue = ((PsiAssignmentExpression)parent).getRExpression();
          if (rValue == null) return;
          if (ExpressionUtils.nonStructuralChildren(rValue)
                             .allMatch(MismatchedCollectionQueryUpdateInspectionBase::isEmptyCollectionInitializer)) {
            return;
          }
          if (ExpressionUtils.nonStructuralChildren(rValue)
                             .allMatch(MismatchedCollectionQueryUpdateInspectionBase::isCollectionInitializer)) {
            makeUpdated();
            return;
          }
        }
        if (parent instanceof PsiPolyadicExpression) {
          IElementType tokenType = ((PsiPolyadicExpression)parent).getOperationTokenType();
          if (tokenType.equals(JavaTokenType.PLUS)) {
            // String concatenation
            makeQueried();
            return;
          }
          if (tokenType.equals(JavaTokenType.EQEQ) || tokenType.equals(JavaTokenType.NE)) {
            return;
          }
        }
        if (parent instanceof PsiInstanceOfExpression) return;
        // Any other reference
        makeUpdated();
        makeQueried();
      }

      private void processQualifiedMethodReference(PsiMethodReferenceExpression expression) {
        final String methodName = expression.getReferenceName();
        if (isQueryMethodName(methodName)) {
          makeQueried();
        }
        if (isUpdateMethodName(methodName)) {
          makeUpdated();
        }
        final PsiMethod method = ObjectUtils.tryCast(expression.resolve(), PsiMethod.class);
        if (method == null ||
            PsiType.VOID.equals(method.getReturnType()) ||
            PsiType.VOID.equals(LambdaUtil.getFunctionalInterfaceReturnType(expression))) {
          return;
        }
        makeQueried();
      }

      private boolean processCollectionMethods(PsiMethodCallExpression call, PsiExpression arg) {
        PsiExpressionList expressionList = call.getArgumentList();
        String name = call.getMethodExpression().getReferenceName();
        if (!COLLECTIONS_ALL.contains(name) || !isCollectionsClassMethod(call)) return false;
        if (COLLECTIONS_QUERIES.contains(name) && !(call.getParent() instanceof PsiExpressionStatement)) {
          makeQueried();
          return true;
        }
        if (COLLECTIONS_UPDATES.contains(name)) {
          int index = ArrayUtil.indexOf(expressionList.getExpressions(), arg);
          if (index == 0) {
            makeUpdated();
          }
          else {
            makeQueried();
          }
          return true;
        }
        return false;
      }

      private void processQualifiedCall(PsiMethodCallExpression call) {
        boolean voidContext = ExpressionUtils.isVoidContext(call);
        String name = call.getMethodExpression().getReferenceName();
        boolean queryQualifier = isQueryMethodName(name);
        boolean updateQualifier = isUpdateMethodName(name);
        if (queryQualifier &&
            (!voidContext || PsiType.VOID.equals(call.getType()) || "toArray".equals(name) && !call.getArgumentList().isEmpty())) {
          makeQueried();
        }
        if (updateQualifier) {
          makeUpdated();
          if (!voidContext) {
            makeQueried();
          }
        }
        if (!queryQualifier && !updateQualifier) {
          if (!isQueryMethod(call)) {
            makeUpdated();
          }
          makeQueried();
        }
      }

      private PsiExpression findEffectiveReference(PsiExpression expression) {
        while (true) {
          PsiElement parent = expression.getParent();
          if (parent instanceof PsiParenthesizedExpression || parent instanceof PsiTypeCastExpression ||
              parent instanceof PsiConditionalExpression) {
            expression = (PsiExpression)parent;
            continue;
          }
          if (parent instanceof PsiReferenceExpression) {
            PsiMethodCallExpression grandParent = ObjectUtils.tryCast(parent.getParent(), PsiMethodCallExpression.class);
            if (DERIVED.test(grandParent)) {
              expression = grandParent;
              continue;
            }
          }
          if (parent instanceof PsiExpressionList) {
            PsiMethodCallExpression grandParent = ObjectUtils.tryCast(parent.getParent(), PsiMethodCallExpression.class);
            if (TRANSFORMED.test(grandParent)) {
              expression = grandParent;
              continue;
            }
          }
          break;
        }
        return expression;
      }
    }
    Visitor visitor = new Visitor();
    context.accept(visitor);
    return info;
  }

  private boolean isQueryMethodName(String methodName) {
    return isQueryUpdateMethodName(methodName, queryNames);
  }

  private boolean isUpdateMethodName(String methodName) {
    return isQueryUpdateMethodName(methodName, updateNames);
  }

  private static boolean isQueryUpdateMethodName(String methodName, Set<String> myNames) {
    if (methodName == null) {
      return false;
    }
    if (myNames.contains(methodName)) {
      return true;
    }
    for (String updateName : myNames) {
      if (methodName.startsWith(updateName)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isCollectionsClassMethod(PsiMethodCallExpression call) {
    final PsiMethod method = call.resolveMethod();
    if (method == null) return false;
    final PsiClass aClass = method.getContainingClass();
    if (aClass == null) return false;
    final String qualifiedName = aClass.getQualifiedName();
    return CommonClassNames.JAVA_UTIL_COLLECTIONS.equals(qualifiedName);
  }

  private static boolean isQueryMethod(@NotNull PsiCallExpression call) {
    PsiType type = call.getType();
    boolean immutable = isImmutable(type);
    // If pure method returns mutable object, then it's possible that further mutation of that object will modify the original collection
    if (!immutable) {
      immutable = call instanceof PsiNewExpression && CollectionUtils.isConcreteCollectionClass(type);
    }
    PsiMethod method = call.resolveMethod();
    if (!immutable && method != null) {
      PsiClass returnType = PsiUtil.resolveClassInClassTypeOnly(method.getReturnType());
      if (returnType instanceof PsiTypeParameter) {
        // method returning unbounded type parameter is unlikely to allow modify original collection via the returned value
        immutable = ((PsiTypeParameter)returnType).getExtendsList().getReferencedTypes().length == 0;
      }
      if (!immutable) {
        immutable = Mutability.getMutability(method).isUnmodifiable();
      }
    }
    return immutable && !SideEffectChecker.mayHaveSideEffects(call);
  }

  static class QueryUpdateInfo {
    boolean updated;
    boolean queried;
  }

}
