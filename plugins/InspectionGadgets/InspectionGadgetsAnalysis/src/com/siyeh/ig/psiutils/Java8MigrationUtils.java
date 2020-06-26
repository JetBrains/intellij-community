// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.psiutils;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInspection.util.LambdaGenerationUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class Java8MigrationUtils {
  @Nullable
  private static Java8MigrationUtils.MapCheckCondition tryExtract(PsiExpression fullCondition,
                                                                  @Nullable PsiStatement statement,
                                                                  boolean treatGetNullAsContainsKey) {
    PsiExpression condition = PsiUtil.skipParenthesizedExprDown(fullCondition);
    boolean negated = false;
    while (condition != null && BoolUtils.isNegation(condition)) {
      negated ^= true;
      condition = BoolUtils.getNegated(condition);
    }
    if (condition == null) return null;
    PsiReferenceExpression valueReference = null;
    boolean containsKey = false;
    PsiMethodCallExpression call;
    if (condition instanceof PsiBinaryExpression) {
      negated ^= ((PsiBinaryExpression)condition).getOperationTokenType().equals(JavaTokenType.EQEQ);
      PsiExpression value = PsiUtil.skipParenthesizedExprDown(ExpressionUtils.getValueComparedWithNull((PsiBinaryExpression)condition));
      if (value instanceof PsiReferenceExpression && statement != null) {
        valueReference = (PsiReferenceExpression)value;
        PsiElement previous = PsiTreeUtil.skipWhitespacesAndCommentsBackward(statement);
        call = tryExtractMapGetCall(valueReference, previous);
      }
      else {
        call = extractMapMethodCall(value, "get");
      }
    }
    else {
      call = extractMapMethodCall(condition, "containsKey");
      containsKey = true;
    }
    if (call == null) return null;
    PsiExpression mapExpression = call.getMethodExpression().getQualifierExpression();
    if (mapExpression == null) return null;
    PsiExpression[] args = call.getArgumentList().getExpressions();
    if (args.length != 1) return null;
    PsiExpression keyExpression = args[0];
    return new Java8MigrationUtils.MapCheckCondition(valueReference, mapExpression, keyExpression, fullCondition, negated, containsKey,
                                                     treatGetNullAsContainsKey);
  }

  @Nullable
  @Contract("_, null -> null")
  private static PsiMethodCallExpression tryExtractMapGetCall(PsiReferenceExpression target, PsiElement element) {
    if (element instanceof PsiDeclarationStatement) {
      PsiDeclarationStatement declaration = (PsiDeclarationStatement)element;
      PsiElement[] elements = declaration.getDeclaredElements();
      if (elements.length > 0) {
        PsiElement lastDeclaration = elements[elements.length - 1];
        if (lastDeclaration instanceof PsiLocalVariable && target.isReferenceTo(lastDeclaration)) {
          PsiLocalVariable var = (PsiLocalVariable)lastDeclaration;
          return extractMapMethodCall(var.getInitializer(), "get");
        }
      }
    }
    PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(element);
    if (assignment != null) {
      PsiExpression lValue = assignment.getLExpression();
      if (lValue instanceof PsiReferenceExpression &&
          EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(target, lValue)) {
        return extractMapMethodCall(assignment.getRExpression(), "get");
      }
    }
    return null;
  }

  /**
   * extracts the call of the Map class
   * @param expression expected to be {@link PsiMethodCallExpression}
   * @param expectedName name of the Map method
   */
  @Contract("null, _ -> null")
  public static PsiMethodCallExpression extractMapMethodCall(PsiExpression expression, @NotNull String expectedName) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (!(expression instanceof PsiMethodCallExpression)) return null;
    PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
    if (!expectedName.equals(methodCallExpression.getMethodExpression().getReferenceName())) return null;
    final PsiMethod method = methodCallExpression.resolveMethod();
    if (method == null) return null;
    PsiMethod[] superMethods = method.findDeepestSuperMethods();
    if (superMethods.length == 0) {
      superMethods = new PsiMethod[]{method};
    }
    return StreamEx.of(superMethods).map(PsiMember::getContainingClass).nonNull().map(PsiClass::getQualifiedName)
             .has(CommonClassNames.JAVA_UTIL_MAP) ? methodCallExpression : null;
  }


  /**
   * Extracts expression, that can be lambda body from Map.put() call
   * @param statement  - put call or block with expected put call inside
   */
  @Nullable
  public static PsiExpression extractLambdaCandidate(Java8MigrationUtils.MapCheckCondition condition, PsiStatement statement) {
    PsiAssignmentExpression assignment;
    PsiExpression putValue = extractPutValue(condition, statement);
    if (putValue != null) {
      // like map.put(key, val = new ArrayList<>());
      assignment = ExpressionUtils.getAssignment(putValue);
    }
    else {
      if (!(statement instanceof PsiBlockStatement)) return null;
      // like val = new ArrayList<>(); map.put(key, val);
      PsiStatement[] statements = ((PsiBlockStatement)statement).getCodeBlock().getStatements();
      if (statements.length != 2) return null;
      putValue = extractPutValue(condition, statements[1]);
      if (!condition.isValueReference(putValue)) return null;
      assignment = ExpressionUtils.getAssignment(statements[0]);
    }
    if (assignment == null) return null;
    PsiExpression lambdaCandidate = assignment.getRExpression();
    if (lambdaCandidate == null || !condition.isValueReference(assignment.getLExpression())) return null;
    if (!LambdaGenerationUtil.canBeUncheckedLambda(lambdaCandidate)) return null;
    return lambdaCandidate;
  }


  /**
   * @return put value
   */
  @Contract("_, null -> null")
  @Nullable
  public static PsiExpression extractPutValue(Java8MigrationUtils.MapCheckCondition condition, PsiStatement statement) {
    if (!(statement instanceof PsiExpressionStatement)) return null;
    PsiMethodCallExpression putCall = extractMapMethodCall(((PsiExpressionStatement)statement).getExpression(), "put");
    if (putCall == null) return null;
    PsiExpression[] putArguments = putCall.getArgumentList().getExpressions();
    return putArguments.length == 2 &&
           condition.isMap(putCall.getMethodExpression().getQualifierExpression()) &&
           condition.isKey(putArguments[0]) ? putArguments[1] : null;
  }


  public interface MapCondition {
  }

  /**
   * Class represents check when working with map: there is 2 ways - when there is a value that matches the key, and when value doesn't exists
   */
  public static final class MapCheckCondition implements MapCondition {
    private final @Nullable PsiReferenceExpression myValueReference;
    private final PsiExpression myMapExpression;
    private final PsiExpression myKeyExpression;
    private final PsiExpression myFullCondition;
    private final boolean myNegated;
    private final boolean myContainsKey;
    private final boolean myTreatGetNullAsContainsKey;

    private MapCheckCondition(@Nullable PsiReferenceExpression valueReference,
                              PsiExpression mapExpression,
                              PsiExpression keyExpression,
                              PsiExpression fullCondition,
                              boolean negated,
                              boolean containsKey,
                              boolean treatGetNullAsContainsKey) {
      myValueReference = valueReference;
      myMapExpression = mapExpression;
      myKeyExpression = keyExpression;
      myFullCondition = fullCondition;
      myNegated = negated;
      myContainsKey = containsKey;
      myTreatGetNullAsContainsKey = treatGetNullAsContainsKey;
    }

    @Nullable
    public PsiReferenceExpression getValueReference() {
      return myValueReference;
    }

    @Nullable
    public PsiExpression getMapExpression() {
      return myMapExpression;
    }

    public PsiExpression getKeyExpression() {
      return myKeyExpression;
    }

    public boolean isContainsKey() {
      return myContainsKey || myTreatGetNullAsContainsKey;
    }

    public boolean isGetNull() {
      return !myContainsKey || myTreatGetNullAsContainsKey;
    }

    @Contract("null -> false")
    public boolean isMap(PsiElement element) {
      return element != null && PsiEquivalenceUtil.areElementsEquivalent(myMapExpression, element);
    }

    @Contract("null -> false")
    public boolean isKey(PsiElement element) {
      return element != null && PsiEquivalenceUtil.areElementsEquivalent(myKeyExpression, element);
    }

    public PsiMethodCallExpression extractGetCall(PsiElement element) {
      if (!(element instanceof PsiExpression)) return null;
      PsiMethodCallExpression getCall = extractMapMethodCall((PsiExpression)element, "get");
      if (getCall == null) return null;
      PsiExpression[] args = getCall.getArgumentList().getExpressions();
      return args.length == 1 && isKey(args[0]) && isMap(getCall.getMethodExpression().getQualifierExpression()) ? getCall : null;
    }

    @Contract("null -> false")
    public boolean isValueReference(PsiElement element) {
      return element != null && myValueReference != null && PsiEquivalenceUtil.areElementsEquivalent(element, myValueReference);
    }

    public <T extends PsiElement> T getExistsBranch(T thenBranch, T elseBranch) {
      return myNegated ? elseBranch : thenBranch;
    }

    public <T extends PsiElement> T getNoneBranch(T thenBranch, T elseBranch) {
      return myNegated ? thenBranch : elseBranch;
    }

    public PsiVariable extractDeclaration() {
      if (myValueReference == null) return null;
      return PsiTreeUtil.getParentOfType(myKeyExpression, PsiVariable.class, true, PsiStatement.class);
    }

    public boolean hasVariable() {
      if (myValueReference == null) return false;
      PsiVariable var = extractDeclaration();
      // has variable, but it used only in condition
      return var == null || ReferencesSearch.search(var).findAll().size() != 1;
    }

    public PsiMethodCallExpression getCheckCall() {
      return PsiTreeUtil.getParentOfType(myMapExpression, PsiMethodCallExpression.class);
    }

    public PsiExpression getFullCondition() {
      return myFullCondition;
    }

    public boolean isMapValueType(@Nullable PsiType type) {
      if (type == null) return false;
      PsiType mapExpressionType = myMapExpression.getType();
      PsiType valueTypeParameter = PsiUtil.substituteTypeParameter(mapExpressionType, CommonClassNames.JAVA_UTIL_MAP, 1, false);
      return valueTypeParameter != null && valueTypeParameter.isAssignableFrom(type);
    }

    @Contract("null, _ -> null")
    public static Java8MigrationUtils.MapCheckCondition fromConditional(PsiElement conditional, boolean treatGetNullAsContainsKey) {
      if (conditional instanceof PsiIfStatement) {
        PsiIfStatement ifStatement = (PsiIfStatement)conditional;
        return tryExtract(ifStatement.getCondition(), ifStatement, treatGetNullAsContainsKey);
      }
      if (conditional instanceof PsiConditionalExpression) {
        PsiConditionalExpression ternary = (PsiConditionalExpression)conditional;
        PsiElement parent = ternary.getParent().getParent();
        return tryExtract(ternary.getCondition(), parent instanceof PsiStatement ? (PsiStatement)parent : null, treatGetNullAsContainsKey);
      }
      return null;
    }
  }

  /**
   * This class is used to store enhanced for loop info when iterating {@link Map} keys or entries.
   * It supports two iteration patterns:
   * 1. for (String value : map.keySet())
   * 2. for (Entry entry : map.entrySet())
   */
  public static final class MapLoopCondition implements MapCondition {
    private final PsiParameter myIterParam;
    private final boolean myIsEntrySet;
    private final PsiReferenceExpression myMapExpression;
    private final PsiVariable myMap;

    private MapLoopCondition(@NotNull PsiParameter iterParam, boolean isEntrySet,
                             @NotNull PsiReferenceExpression mapExpression, @NotNull PsiVariable map) {
      myIterParam = iterParam;
      myIsEntrySet = isEntrySet;
      myMapExpression = mapExpression;
      myMap = map;
    }

    /**
     * Try to extract {@link Map#put(Object, Object)} call from for each statement body.
     * This call is expected to be applied to the same map instance as for each iteration, otherwise null will be returned.
     *
     * @see ControlFlowUtils#stripBraces(PsiStatement)
     */
    @Nullable
    public PsiMethodCallExpression extractPut(@NotNull PsiForeachStatement statement) {
      PsiExpressionStatement putStatement =
        ObjectUtils.tryCast(ControlFlowUtils.stripBraces(statement.getBody()), PsiExpressionStatement.class);
      if (putStatement == null) return null;
      PsiMethodCallExpression putCall = extractMapMethodCall(putStatement.getExpression(), "put");
      if (putCall == null || !isMapRef(putCall.getMethodExpression().getQualifierExpression())) return null;
      return putCall;
    }

    /**
     * Check if given expression either entry.getKey() call (for entry set based loop) or
     * loop parameter reference (for key based loop).
     */
    public boolean isKeyAccess(@NotNull PsiExpression expression) {
      if (!myIsEntrySet) {
        PsiReferenceExpression keyRef = ObjectUtils.tryCast(expression, PsiReferenceExpression.class);
        return keyRef != null && keyRef.resolve() == myIterParam;
      }
      return isParamCall(expression, "getKey");
    }

    /**
     * Check if given expression is entry.getValue() call (for entry set based loop) or map.get(key) (for key based loop).
     */
    public boolean isValueAccess(@NotNull PsiExpression expression) {
      if (myIsEntrySet) return isParamCall(expression, "getValue");
      return isGetCall(expression);
    }

    private boolean isGetCall(@NotNull PsiExpression expression) {
      PsiMethodCallExpression call = ObjectUtils.tryCast(expression, PsiMethodCallExpression.class);
      if (call == null) return false;
      String name = call.getMethodExpression().getReferenceName();
      if (!"get".equals(name) || !isMapRef(call.getMethodExpression().getQualifierExpression())) return false;
      PsiExpression[] args = call.getArgumentList().getExpressions();
      return args.length == 1 && ExpressionUtils.isReferenceTo(args[0], myIterParam);
    }

    /**
     * Check if given call is invoked on loop iteration parameter.
     */
    public boolean isParamCall(@NotNull PsiMethodCallExpression call) {
      PsiReferenceExpression qualifier =
        ObjectUtils.tryCast(call.getMethodExpression().getQualifierExpression(), PsiReferenceExpression.class);
      return qualifier != null && qualifier.resolve() == myIterParam;
    }

    public PsiParameter getIterParam() {
      return myIterParam;
    }

    public PsiVariable getMap() {
      return myMap;
    }

    public boolean isEntrySet() {
      return myIsEntrySet;
    }

    private boolean isParamCall(@NotNull PsiExpression expression, @NotNull String expectedName) {
      PsiMethodCallExpression call = ObjectUtils.tryCast(expression, PsiMethodCallExpression.class);
      if (call == null) return false;
      String name = call.getMethodExpression().getReferenceName();
      return expectedName.equals(name) && isParamCall(call);
    }

    private boolean isMapRef(@Nullable PsiElement element) {
      return element != null && PsiEquivalenceUtil.areElementsEquivalent(myMapExpression, element);
    }

    /**
     * Create {@link MapLoopCondition} from enhanced for statement.
     * Loop condition instance created only if iteration is done using {@link Map#keySet()} or {@link Map#entrySet()} methods.
     */
    @Nullable
    public static MapLoopCondition create(@NotNull PsiForeachStatement statement) {
      PsiExpression iteratedValue = statement.getIteratedValue();
      PsiParameter iterParam = statement.getIterationParameter();
      PsiMethodCallExpression iterCall = extractMapMethodCall(iteratedValue, "keySet");
      if (iterCall != null) return create(iterParam, false, iterCall.getMethodExpression().getQualifierExpression());

      iterCall = extractMapMethodCall(iteratedValue, "entrySet");
      if (iterCall != null) return create(iterParam, true, iterCall.getMethodExpression().getQualifierExpression());

      return null;
    }

    private static MapLoopCondition create(@NotNull PsiParameter iterParam, boolean isEntrySet, @Nullable PsiExpression qualifier) {
      PsiReferenceExpression ref = ObjectUtils.tryCast(qualifier, PsiReferenceExpression.class);
      if (ref == null) return null;
      PsiVariable map = ObjectUtils.tryCast(ref.resolve(), PsiVariable.class);
      if (map == null) return null;
      return new MapLoopCondition(iterParam, isEntrySet, ref, map);
    }
  }
}