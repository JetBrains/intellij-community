/*
 * Copyright 2003-2021 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.impl.UnusedSymbolUtil;
import com.intellij.codeInsight.options.JavaClassValidator;
import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.codeInspection.dataFlow.Mutability;
import com.intellij.codeInspection.options.OptPane;
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
import com.siyeh.ig.psiutils.*;
import com.siyeh.ig.ui.ExternalizableStringSet;
import one.util.streamex.StreamEx;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.intellij.codeInspection.options.OptPane.*;
import static com.siyeh.ig.psiutils.ClassUtils.isImmutable;

public class MismatchedCollectionQueryUpdateInspection extends BaseInspection {

  private static final CallMatcher TRANSFORMED = CallMatcher.staticCall(
    CommonClassNames.JAVA_UTIL_COLLECTIONS, "asLifoQueue", "checkedCollection", "checkedList", "checkedMap", "checkedNavigableMap",
    "checkedNavigableSet", "checkedQueue", "checkedSet", "checkedSortedMap", "checkedSortedSet", "newSetFromMap", "synchronizedCollection",
    "synchronizedList", "synchronizedMap", "synchronizedNavigableMap", "synchronizedNavigableSet", "synchronizedSet",
    "synchronizedSortedMap", "synchronizedSortedSet");
  private static final CallMatcher DERIVED = CallMatcher.anyOf(
    CollectionUtils.DERIVED_COLLECTION,
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_LIST, "subList"),
    CallMatcher.instanceCall("java.util.SortedMap", "headMap", "tailMap", "subMap"),
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_SORTED_SET, "headSet", "tailSet", "subSet"));
  private static final CallMatcher COLLECTION_SAFE_ARGUMENT_METHODS =
    CallMatcher.anyOf(
      CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_COLLECTION, "addAll", "removeAll", "containsAll", "remove"),
      CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_MAP, "putAll", "remove")
    );
  private static final @NonNls Set<String> COLLECTIONS_QUERIES =
    Set.of("binarySearch", "disjoint", "indexOfSubList", "lastIndexOfSubList", "max", "min");
  private static final @NonNls Set<String> COLLECTIONS_UPDATES = Set.of("addAll", "fill", "copy", "replaceAll", "sort");
  private static final Set<String> COLLECTIONS_ALL =
    StreamEx.of(COLLECTIONS_QUERIES).append(COLLECTIONS_UPDATES).toImmutableSet();
  private static final Set<String> defaultQueryNames =
    Set.of("contains", "copyInto", "equals", "forEach", "get", "hashCode", "indexOf", "iterator", "lastIndexOf", "parallelStream", "peek",
           "propertyNames", "save", "size", "store", "stream", "toArray", "toString", "write");
  private static final Set<String> defaultUpdateNames =
    Set.of("add", "clear", "insert", "load", "merge", "offer", "poll", "pop", "push", "put", "remove", "replace", "retain", "sort", "set",
           "take");
  @SuppressWarnings("PublicField")
  public final ExternalizableStringSet queryNames = new ExternalizableStringSet(defaultQueryNames.stream().sorted()
                                                                                    .toArray(String[]::new));
  @SuppressWarnings("PublicField")
  public final ExternalizableStringSet updateNames = new ExternalizableStringSet(defaultUpdateNames.stream().sorted()
                                                                                     .toArray(String[]::new));
  @SuppressWarnings("PublicField")
  public final ExternalizableStringSet ignoredClasses = new ExternalizableStringSet();

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      horizontalStack(
        stringList("queryNames", InspectionGadgetsBundle.message("query.label")),
        stringList("updateNames", InspectionGadgetsBundle.message("update.label"))
      ),
      stringList("ignoredClasses", InspectionGadgetsBundle.message("ignored.class.label"),
                 new JavaClassValidator().withTitle(InspectionGadgetsBundle.message("ignored.class.names"))
                  .withSuperClass(CommonClassNames.JAVA_UTIL_COLLECTION, CommonClassNames.JAVA_UTIL_MAP))
    );
  }

  @Pattern(VALID_ID_PATTERN)
  @Override
  @NotNull
  public String getID() {
    return "MismatchedQueryAndUpdateOfCollection";
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

  private static QueryUpdateInfo getCollectionQueryUpdateInfo(@Nullable PsiVariable variable,
                                                              PsiElement context,
                                                              Set<String> queryNames,
                                                              Set<String> updateNames) {
    QueryUpdateInfo info = new QueryUpdateInfo();
    Visitor visitor = new Visitor(variable, queryNames, updateNames, info);
    context.accept(visitor);
    return info;
  }

  private static class Visitor extends JavaRecursiveElementWalkingVisitor {
    final PsiVariable myVariable;
    final Set<String> myQueryNames;
    final Set<String> myUpdateNames;
    final QueryUpdateInfo myInfo;

    private Visitor(@Nullable PsiVariable variable, Set<String> queryNames, Set<String> updateNames, QueryUpdateInfo info) {
      myVariable = variable;
      myQueryNames = queryNames;
      myUpdateNames = updateNames;
      myInfo = info;
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression ref) {
      super.visitReferenceExpression(ref);
      if (myVariable == null) {
        if (ref.getQualifierExpression() == null) {
          makeUpdated();
          makeQueried();
        }
      } else if (ref.isReferenceTo(myVariable)) {
        process(findEffectiveReference(ref));
      }
    }

    @Override
    public void visitThisExpression(@NotNull PsiThisExpression expression) {
      super.visitThisExpression(expression);
      if (myVariable == null) {
        process(findEffectiveReference(expression));
      }
    }

    private void makeUpdated() {
      myInfo.updated = true;
      if (myInfo.queried) {
        stopWalking();
      }
    }

    private void makeQueried() {
      myInfo.queried = true;
      if (myInfo.updated) {
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
      PsiElement grandParent = skipAssigmentExprUp(parent);
      if (parent instanceof PsiExpressionList ||
          (parent instanceof PsiAssignmentExpression && grandParent instanceof PsiExpressionList)) {
        PsiExpressionList args = (PsiExpressionList)(parent instanceof PsiExpressionList ? parent : grandParent);
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
          .allMatch(MismatchedCollectionQueryUpdateInspection::isEmptyCollectionInitializer)) {
          return;
        }
        if (ExpressionUtils.nonStructuralChildren(rValue)
          .allMatch(MismatchedCollectionQueryUpdateInspection::isCollectionInitializer)) {
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
      if (parent instanceof PsiAssertStatement && ((PsiAssertStatement)parent).getAssertDescription() == reference) {
        makeQueried();
        return;
      }
      if (parent instanceof PsiInstanceOfExpression || parent instanceof PsiSynchronizedStatement) return;
      // Any other reference
      makeUpdated();
      makeQueried();
    }

    private void processQualifiedMethodReference(PsiMethodReferenceExpression expression) {
      final String methodName = expression.getReferenceName();
      if (isQueryUpdateMethodName(methodName, myQueryNames)) {
        makeQueried();
      }
      if (isQueryUpdateMethodName(methodName, myUpdateNames)) {
        makeUpdated();
      }
      final PsiMethod method = ObjectUtils.tryCast(expression.resolve(), PsiMethod.class);
      if (method != null &&
          (!PsiTypes.voidType().equals(method.getReturnType()) &&
           !PsiTypes.voidType().equals(LambdaUtil.getFunctionalInterfaceReturnType(expression)) ||
           ContainerUtil.or(method.getParameterList().getParameters(), p -> LambdaUtil.isFunctionalType(p.getType())))) {
        makeQueried();
      }
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
      boolean queryQualifier = isQueryUpdateMethodName(name, myQueryNames);
      boolean updateQualifier = isQueryUpdateMethodName(name, myUpdateNames);
      if (queryQualifier &&
          (!voidContext || PsiTypes.voidType().equals(call.getType()) || "toArray".equals(name) && !call.getArgumentList().isEmpty())) {
        makeQueried();
      }
      if (updateQualifier) {
        makeUpdated();
        if (!voidContext) {
          makeQueried();
        }
        else {
          for (PsiExpression arg : call.getArgumentList().getExpressions()) {
            PsiParameter parameter = MethodCallUtils.getParameterForArgument(arg);
            if (parameter != null && LambdaUtil.isFunctionalType(parameter.getType())) {
              if (ExpressionUtils.nonStructuralChildren(arg).anyMatch(e -> mayHaveSideEffect(e))) {
                makeQueried();
                break;
              }
            }
          }
          if (call.getArgumentList().getExpressionCount() == 2 &&
              ("poll".equals(name) || "pollFirst".equals(name) || "pollLast".equals(name)) &&
              TypeUtils.variableHasTypeOrSubtype(myVariable, "java.util.concurrent.BlockingQueue")) {
            // poll(timeout, unit) on a blocking queue/dequeue may be considered querying, even if the result is not used,
            // because the thread will be blocked until a value is received (or a timeout happens).
            makeQueried();
          }
          else if (("take".equals(name) || "takeFirst".equals(name) || "takeLast".equals(name)) &&
                   TypeUtils.variableHasTypeOrSubtype(myVariable, "java.util.concurrent.BlockingQueue")) {
            // take() on a blocking queue/dequeue may be considered querying, even if the result is not used.
            // because the thread will be blocked until a value is received.
            makeQueried();
          }
        }
      }
      if (!queryQualifier && !updateQualifier) {
        if (!isQueryMethod(call)) {
          makeUpdated();
        }
        makeQueried();
      }
    }

    private static boolean mayHaveSideEffect(PsiExpression fn) {
      if (fn instanceof PsiLambdaExpression) {
        PsiElement body = ((PsiLambdaExpression)fn).getBody();
        if (body != null) {
          return SideEffectChecker.mayHaveSideEffects(body, x -> false);
        }
      }
      if (fn instanceof PsiMethodReferenceExpression) {
        PsiElement target = ((PsiMethodReferenceExpression)fn).resolve();
        return !(target instanceof PsiMethod) || !JavaMethodContractUtil.isPure((PsiMethod)target);
      }
      return true;
    }
  }

  private static PsiExpression findEffectiveReference(PsiExpression expression) {
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

  private static boolean isEmptyCollectionInitializer(PsiExpression initializer) {
    if (!(initializer instanceof PsiNewExpression newExpression)) {
      return ConstructionUtils.isEmptyCollectionInitializer(initializer);
    }
    final PsiExpressionList argumentList = newExpression.getArgumentList();
    if (argumentList == null) {
      return false;
    }
    PsiMethod ctor = newExpression.resolveMethod();
    if (ctor == null) return true;
    PsiParameter[] parameters = ctor.getParameterList().getParameters();
    final PsiExpression[] arguments = argumentList.getExpressions();
    if (ctor.isVarArgs() && arguments.length >= parameters.length) {
      return false;
    }
    for (PsiParameter parameter : parameters) {
      PsiType type = parameter.getType();
      if (CollectionUtils.isCollectionClassOrInterface(type)) {
        return false;
      }
      if (type instanceof PsiArrayType && !(type instanceof PsiEllipsisType)) {
        return false;
      }
    }
    return true;
  }

  private static boolean isCollectionInitializer(PsiExpression initializer) {
    return isEmptyCollectionInitializer(initializer) || ConstructionUtils.isPrepopulatedCollectionInitializer(initializer);
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

  private static PsiElement skipAssigmentExprUp(@Nullable PsiElement parent) {
    parent = PsiUtil.skipParenthesizedExprUp(parent);
    while (parent instanceof PsiAssignmentExpression) {
      parent = PsiUtil.skipParenthesizedExprUp(parent.getParent());
    }
    return parent;
  }

  private static class QueryUpdateInfo {
    boolean updated;
    boolean queried;
  }

  private class MismatchedCollectionQueryUpdateVisitor extends BaseInspectionVisitor {
    private void register(PsiVariable variable, boolean written) {
      if (written) {
        PsiExpression initializer = variable.getInitializer();
        if (initializer != null) {
          List<PsiExpression> expressions = ExpressionUtils.nonStructuralChildren(initializer).toList();
          if (!ContainerUtil.and(expressions, MismatchedCollectionQueryUpdateInspection::isCollectionInitializer)) {
            expressions.stream().filter(MismatchedCollectionQueryUpdateInspection::isCollectionInitializer)
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
      QueryUpdateInfo info = getCollectionQueryUpdateInfo(field, updateNames, queryNames, ignoredClasses);
      if (info == null) return;
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
      QueryUpdateInfo info = getCollectionQueryUpdateInfo(variable, updateNames, queryNames, ignoredClasses);
      if (info == null) return;
      final boolean written = info.updated || updatedViaInitializer(variable);
      final boolean read = info.queried || queriedViaInitializer(variable);
      if (read != written) {
        register(variable, written);
      }
    }

    private boolean updatedViaInitializer(PsiVariable variable) {
      final PsiExpression initializer = variable.getInitializer();
      if (initializer != null &&
          !ExpressionUtils.nonStructuralChildren(initializer)
            .allMatch(MismatchedCollectionQueryUpdateInspection::isEmptyCollectionInitializer)) {
        return true;
      }
      if (initializer instanceof PsiNewExpression newExpression) {
        final PsiAnonymousClass anonymousClass = newExpression.getAnonymousClass();
        if (anonymousClass != null) {
          if (getCollectionQueryUpdateInfo(null, anonymousClass, queryNames, updateNames).updated) {
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
               .noneMatch(MismatchedCollectionQueryUpdateInspection::isCollectionInitializer);
    }
  }

  private static boolean checkVariable(PsiVariable variable, PsiElement context, Set<String> ignoredClasses) {
    if (context == null) {
      return false;
    }
    final PsiType type = variable.getType();
    if (!CollectionUtils.isCollectionClassOrInterface(type)) {
      return false;
    }
    return !ContainerUtil.exists(ignoredClasses, className -> InheritanceUtil.isInheritor(type, className));
  }

  private static QueryUpdateInfo getCollectionQueryUpdateInfo(@NotNull PsiField field,
                                                              Set<String> updateNames,
                                                              Set<String> queryNames,
                                                              Set<String> ignoredClasses) {
    if (!field.hasModifierProperty(PsiModifier.PRIVATE)) {
      PsiClass aClass = field.getContainingClass();
      if (aClass == null || !aClass.hasModifierProperty(PsiModifier.PRIVATE) || field.hasModifierProperty(PsiModifier.PUBLIC)) {
        // Public field within private class can be written/read via reflection even without setAccessible hacks,
        // so we don't analyze such fields to reduce false-positives
        return null;
      }
    }
    final PsiClass containingClass = PsiUtil.getTopLevelClass(field);
    if (!checkVariable(field, containingClass, ignoredClasses)) {
      return null;
    }
    return getCollectionQueryUpdateInfo(field, containingClass, queryNames, updateNames);
  }

  private static QueryUpdateInfo getCollectionQueryUpdateInfo(@NotNull PsiLocalVariable variable,
                                                              Set<String> updateNames,
                                                              Set<String> queryNames,
                                                              Set<String> ignoredClasses) {
    final PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
    if (!checkVariable(variable, codeBlock, ignoredClasses)) {
      return null;
    }
    return getCollectionQueryUpdateInfo(variable, codeBlock, queryNames, updateNames);
  }

  /**
   * @param collectCall a method call returning a list (e.g. {@code Stream.of(1, 2, 3).collect(Collectors.toList())})
   * @return {@code true} if it's known that the result of {@code collectCall} is never updated;
   * {@code false} if updated or not known
   */
  @Contract("null -> false")
  public static boolean isUnmodified(@Nullable PsiMethodCallExpression collectCall) {
    if (collectCall == null) return false;
    final PsiExpression effectiveReference =  findEffectiveReference(collectCall);
    QueryUpdateInfo info = new QueryUpdateInfo();
    new Visitor(null, defaultQueryNames, defaultUpdateNames, info).process(effectiveReference);
    if (!info.updated) return true;
    final PsiElement parent = effectiveReference.getParent();
    if (!(parent instanceof PsiLocalVariable || parent instanceof PsiField)) return false;
    if (parent instanceof PsiField) {
      info = getCollectionQueryUpdateInfo((PsiField)parent, defaultUpdateNames, defaultQueryNames, Collections.emptySet());
    } else {
      info = getCollectionQueryUpdateInfo((PsiLocalVariable)parent, defaultUpdateNames, defaultQueryNames, Collections.emptySet());
    }
    return info != null && !info.updated;
  }
}
