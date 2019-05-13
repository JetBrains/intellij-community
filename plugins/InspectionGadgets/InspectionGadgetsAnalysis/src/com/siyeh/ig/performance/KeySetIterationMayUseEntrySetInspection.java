/*
 * Copyright 2008-2017 Bas Leijdekkers
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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.RedundantCastUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.intellij.util.ObjectUtils.tryCast;

public class KeySetIterationMayUseEntrySetInspection extends BaseInspection {
  private static final CallMatcher ITERABLE_FOR_EACH = CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_ITERABLE, "forEach")
    .parameterTypes(CommonClassNames.JAVA_UTIL_FUNCTION_CONSUMER);
  private static final CallMatcher MAP_KEY_SET = CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_MAP, "keySet")
    .parameterCount(0);
  private static final CallMatcher MAP_GET = CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_MAP, "get")
    .parameterTypes(CommonClassNames.JAVA_LANG_OBJECT);
  
  private enum ReplacementMode {
    FOR_EACH("Map.forEach()"), VALUES("values()"), ENTRY_SET("entrySet()");

    private final String myDisplayName;

    ReplacementMode(String displayName) {
      myDisplayName = displayName;
    }
  }
  
  @Override
  @NotNull
  @Nls
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("key.set.iteration.may.use.entry.set.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    ReplacementMode mode = (ReplacementMode)infos[0];
    return InspectionGadgetsBundle.message("key.set.iteration.may.use.entry.set.problem.descriptor", mode.myDisplayName);
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new KeySetIterationMapUseEntrySetFix((ReplacementMode)infos[0]);
  }

  private static class KeySetIterationMapUseEntrySetFix extends InspectionGadgetsFix {
    private final ReplacementMode myMode;

    KeySetIterationMapUseEntrySetFix(ReplacementMode mode) {
      myMode = mode;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", myMode.myDisplayName);
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("key.set.iteration.may.use.entry.set.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      PsiExpression expression = tryCast(descriptor.getPsiElement(), PsiExpression.class);
      if (expression == null) return;
      final PsiVariable toRemove;
      final PsiExpression keySetExpression;
      if (expression instanceof PsiReferenceExpression) {
        toRemove = ExpressionUtils.resolveLocalVariable(expression);
        if (toRemove == null) return;
        keySetExpression = PsiUtil.skipParenthesizedExprDown(toRemove.getInitializer());
      }
      else {
        toRemove = null;
        keySetExpression = expression;
      }
      PsiReferenceExpression mapRef = getMapReferenceFromKeySetCall(keySetExpression);
      if (mapRef == null) return;
      if (myMode == ReplacementMode.FOR_EACH) {
        processLambda(project, expression, mapRef);
      } else {
        processLoop(project, expression, mapRef, myMode);
      }
      if (toRemove != null && ReferencesSearch.search(toRemove).findFirst() == null) {
        final PsiElement statement = toRemove.getParent();
        if (statement instanceof PsiDeclarationStatement && ((PsiDeclarationStatement)statement).getDeclaredElements().length == 1) {
          statement.delete();
        }
        else {
          toRemove.delete();
        }
      }
    }

    private static void processLambda(Project project, PsiExpression iteratedValue, PsiReferenceExpression mapRef) {
      PsiMethodCallExpression forEachCall = ExpressionUtils.getCallForQualifier(iteratedValue);
      if (!ITERABLE_FOR_EACH.test(forEachCall)) return;
      PsiExpression[] args = forEachCall.getArgumentList().getExpressions();
      PsiLambdaExpression lambda = tryCast(PsiUtil.skipParenthesizedExprDown(args[0]), PsiLambdaExpression.class);
      if (lambda == null) return;
      PsiParameterList parameterList = lambda.getParameterList();
      if (parameterList.getParametersCount() != 1) return;
      PsiElement lambdaBody = lambda.getBody();
      if (lambdaBody == null) return;
      PsiParameter keyParameter = parameterList.getParameters()[0];
      mapRef = (PsiReferenceExpression)new CommentTracker().replaceAndRestoreComments(iteratedValue, mapRef);
      PsiType valueType = PsiUtil.substituteTypeParameter(mapRef.getType(), CommonClassNames.JAVA_UTIL_MAP, 1, true);
      List<PsiExpression> accesses = ParameterAccessCollector.collectParameterAccesses(keyParameter, mapRef, lambdaBody);
      String valueName = tryReuseValueVariable(lambdaBody, accesses, valueType);
      if (valueName == null) {
        valueName = new VariableNameGenerator(lambdaBody, VariableKind.PARAMETER).byType(valueType)
          .byName("k".equals(keyParameter.getName()) ? "v" : "value").generate(false);
      }
      for (PsiExpression access : accesses) {
        if (access instanceof PsiMethodCallExpression && access.isValid()) {
          new CommentTracker().replaceAndRestoreComments(access, valueName);
        }
      }
      String newLambdaText = "(" + keyParameter.getName() + "," + valueName + ")->" + lambdaBody.getText();
      PsiExpression newLambda = JavaPsiFacade.getElementFactory(project).createExpressionFromText(newLambdaText, lambda);
      lambda.replace(newLambda);
    }

    private static String tryReuseValueVariable(PsiElement scope, List<PsiExpression> accesses, PsiType wantedType) {
      for (PsiExpression access : accesses) {
        if (access instanceof PsiMethodCallExpression) {
          PsiElement parent = ParenthesesUtils.getParentSkipParentheses(access);
          if (parent instanceof PsiLocalVariable) {
            PsiLocalVariable var = (PsiLocalVariable)parent;
            PsiElement varParent = var.getParent();
            if (varParent instanceof PsiDeclarationStatement &&
                varParent.getParent() == scope &&
                var.getType().equals(wantedType) &&
                !VariableAccessUtils.variableIsAssigned(var, scope)) {
              String valueName = var.getName();
              new CommentTracker().deleteAndRestoreComments(var);
              return valueName;
            }
          }
        }
      }
      return null;
    }

    private static void processLoop(Project project, PsiExpression iteratedValue, PsiReferenceExpression mapRef, ReplacementMode mode) {
      PsiForeachStatement foreachStatement = tryCast(ParenthesesUtils.getParentSkipParentheses(iteratedValue), PsiForeachStatement.class);
      if (foreachStatement == null) return;

      final PsiMethodCallExpression newIterationValue =
        (PsiMethodCallExpression)new CommentTracker().replaceAndRestoreComments(iteratedValue, mapRef.getText() + "."+mode.myDisplayName);
      mapRef = (PsiReferenceExpression)newIterationValue.getMethodExpression().getQualifierExpression();
      PsiType collectionType = newIterationValue.getType();
      PsiType newParameterType = PsiUtil.substituteTypeParameter(collectionType, CommonClassNames.JAVA_UTIL_COLLECTION, 0, true);
      boolean insertCast = false;
      if (newParameterType == null) {
        newParameterType = TypeUtils.getObjectType(foreachStatement);
        insertCast = mode == ReplacementMode.ENTRY_SET;
      }
      final PsiParameter keyParameter = foreachStatement.getIterationParameter();
      final String keyParameterText = keyParameter.getText();
      final String keyTypeText = keyParameter.getType().getCanonicalText();
      PsiStatement body = Objects.requireNonNull(foreachStatement.getBody());
      List<PsiExpression> accesses = ParameterAccessCollector.collectParameterAccesses(keyParameter, mapRef, body);
      String loopParameterName = null;
      if (mode == ReplacementMode.VALUES && body instanceof PsiBlockStatement) {
        loopParameterName = tryReuseValueVariable(((PsiBlockStatement)body).getCodeBlock(), accesses, newParameterType);
      }
      if (loopParameterName == null) {
        loopParameterName = createNewVariableName(foreachStatement, newParameterType, mode);
      }
      final PsiParameter loopParameter = JavaPsiFacade.getElementFactory(project).createParameter(loopParameterName, newParameterType);
      if (keyParameter.hasModifierProperty(PsiModifier.FINAL)) {
        final PsiModifierList modifierList = loopParameter.getModifierList();
        if (modifierList != null) {
          modifierList.setModifierProperty(PsiModifier.FINAL, true);
        }
      }
      keyParameter.replace(loopParameter);
      String replacement = insertCast ? "(("+ CommonClassNames.JAVA_UTIL_MAP_ENTRY+")" + loopParameterName + ')' : loopParameterName;
      if (body instanceof PsiBlockStatement && 
          accesses.stream().filter(access -> !(access instanceof PsiMethodCallExpression)).count() > 1) {
        PsiCodeBlock codeBlock = ((PsiBlockStatement)body).getCodeBlock();
        PsiJavaToken brace = codeBlock.getLBrace();
        if (brace != null) {
          String keyVariableText = keyParameterText + "=" + replacement + ".getKey();";
          PsiStatement keyDeclaration = JavaPsiFacade.getElementFactory(project).createStatementFromText(keyVariableText, body);
          codeBlock.addAfter(keyDeclaration, brace);
          accesses = StreamEx.of(accesses).select(PsiMethodCallExpression.class).collect(Collectors.toList());
        }
      }
      replaceParameterAccess(accesses, keyTypeText, replacement, mode);
    }

    private static void replaceParameterAccess(@NotNull List<PsiExpression> accesses,
                                               @NotNull String typeText,
                                               @NonNls String variableName,
                                               @NotNull ReplacementMode mode) {
      final String keyAccess = '(' + typeText + ')' + variableName + ".getKey()";
      for (PsiExpression access : accesses) {
        if (access instanceof PsiMethodCallExpression) {
          if (!access.isValid()) continue;
          String replacement = mode == ReplacementMode.VALUES ? variableName : variableName + ".getValue()";
          PsiReplacementUtil.replaceExpression(access, replacement);
        }
        else {
          assert mode == ReplacementMode.ENTRY_SET;
          final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)
            PsiReplacementUtil.replaceExpressionAndShorten(access, keyAccess);
          if (RedundantCastUtil.isCastRedundant(typeCastExpression)) {
            final PsiExpression operand = typeCastExpression.getOperand();
            assert operand != null;
            PsiElement parent = typeCastExpression.getParent();
            typeCastExpression.replace(operand);
            if (parent instanceof PsiParenthesizedExpression) {
              ParenthesesUtils.removeParentheses((PsiExpression)parent, false);
            }
          }
        }
      }
    }

    @NotNull
    private static String createNewVariableName(@NotNull PsiElement scope, @NotNull PsiType type, ReplacementMode mode) {
      VariableNameGenerator generator = new VariableNameGenerator(scope, VariableKind.LOCAL_VARIABLE);
      if (mode == ReplacementMode.VALUES) {
        generator.byType(type).byName("value", "val", "v");
      } else {
        // Simple entry/e name is preferred over stringStringEntry, etc. which is generated from type
        generator.byName("entry", "e").byType(type);
      }
      return generator.generate(true);
    }

    private static class ParameterAccessCollector extends JavaRecursiveElementWalkingVisitor {
      private final PsiReferenceExpression myMapReference;
      private final PsiParameter myParameter;
      private final List<PsiExpression> myParameterAccesses = new ArrayList<>();

      ParameterAccessCollector(PsiParameter parameter, PsiReferenceExpression mapReference) {
        this.myParameter = parameter;
        this.myMapReference = mapReference;
      }

      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        if (expression.getQualifierExpression() != null || !expression.isReferenceTo(myParameter)) {
          return;
        }
        if (!collectValueUsage(expression)) {
          myParameterAccesses.add(expression);
        }
      }

      private boolean collectValueUsage(PsiReferenceExpression expression) {
        final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(expression);
        if (!(parent instanceof PsiExpressionList)) return false;
        final PsiElement grandParent = parent.getParent();
        if (!(grandParent instanceof PsiMethodCallExpression)) return false;
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
        if (!MAP_GET.test(methodCallExpression)) return false;
        PsiExpression qualifier = ParenthesesUtils.stripParentheses(methodCallExpression.getMethodExpression().getQualifierExpression());
        if (!(qualifier instanceof PsiReferenceExpression)) return false;
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifier;
        if (!EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(myMapReference, referenceExpression)) return false;
        myParameterAccesses.add(methodCallExpression);
        return true;
      }

      List<PsiExpression> getParameterAccesses() {
        Collections.reverse(myParameterAccesses);
        return myParameterAccesses;
      }

      static List<PsiExpression> collectParameterAccesses(PsiParameter parameter,
                                                          PsiReferenceExpression mapReference,
                                                          PsiElement body) {
        final ParameterAccessCollector collector = new ParameterAccessCollector(parameter, mapReference);
        body.accept(collector);
        return collector.getParameterAccesses();
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new KeySetIterationMayUseEntrySetVisitor();
  }

  private static class KeySetIterationMayUseEntrySetVisitor extends BaseInspectionVisitor {

    @Override
    public void visitForeachStatement(PsiForeachStatement statement) {
      super.visitForeachStatement(statement);
      final PsiExpression iteratedValue = ParenthesesUtils.stripParentheses(statement.getIteratedValue());
      final PsiExpression iteratedExpression = getIteratedExpression(iteratedValue);
      if (iteratedExpression == null) return;
      final PsiParameter parameter = statement.getIterationParameter();
      PsiElement context = statement.getBody();
      if (context == null) return;
      PsiReferenceExpression mapExpression = getMapReferenceFromKeySetCall(iteratedExpression);
      if (mapExpression == null) return;
      final GetValueFromMapChecker checker = new GetValueFromMapChecker(mapExpression, parameter);
      context.accept(checker);
      if (checker.isGetValueFromMap()) {
        registerError(iteratedValue, checker.isKeyUsedExplicitly() ? ReplacementMode.ENTRY_SET : ReplacementMode.VALUES);
      }
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression call) {
      if (!ITERABLE_FOR_EACH.test(call)) return;
      PsiExpression qualifier = ParenthesesUtils.stripParentheses(call.getMethodExpression().getQualifierExpression());
      PsiExpression expression = getIteratedExpression(qualifier);
      PsiReferenceExpression mapExpression = getMapReferenceFromKeySetCall(expression);
      if (mapExpression == null) return;
      PsiExpression arg = PsiUtil.skipParenthesizedExprDown(call.getArgumentList().getExpressions()[0]);
      PsiLambdaExpression lambda = tryCast(arg, PsiLambdaExpression.class);
      if (lambda == null) return;
      PsiParameter[] parameters = lambda.getParameterList().getParameters();
      if (parameters.length != 1) return;
      PsiElement body = lambda.getBody();
      if (body == null) return;
      PsiParameter key = parameters[0];
      final GetValueFromMapChecker checker = new GetValueFromMapChecker(mapExpression, key);
      body.accept(checker);
      if (!checker.isGetValueFromMap()) return;
      registerError(qualifier, ReplacementMode.FOR_EACH);
    }
  }

  @Nullable
  @Contract("null -> null")
  private static PsiExpression getIteratedExpression(PsiExpression iteratedValue) {
    iteratedValue = PsiUtil.skipParenthesizedExprDown(iteratedValue);
    PsiLocalVariable variable = ExpressionUtils.resolveLocalVariable(iteratedValue);
    if (variable != null) {
      final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(variable, PsiMethod.class);
      if (VariableAccessUtils.variableIsAssignedAtPoint(variable, containingMethod, iteratedValue)) {
        return null;
      }
      return PsiUtil.skipParenthesizedExprDown(variable.getInitializer());
    }
    return iteratedValue;
  }

  @Contract("null -> null")
  private static PsiReferenceExpression getMapReferenceFromKeySetCall(PsiExpression keySetCandidate) {
    if (!(keySetCandidate instanceof PsiMethodCallExpression)) return null;
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)keySetCandidate;
    if (!MAP_KEY_SET.test(methodCallExpression)) return null;
    PsiExpression qualifier = methodCallExpression.getMethodExpression().getQualifierExpression();
    return tryCast(PsiUtil.skipParenthesizedExprDown(qualifier), PsiReferenceExpression.class);
  }

  private static class GetValueFromMapChecker extends JavaRecursiveElementWalkingVisitor {
    private final PsiVariable myKey;
    private final PsiReferenceExpression myMapReference;
    private boolean myGetValueFromMap;
    private boolean myKeyUsedExplicitly;
    private boolean myTainted;

    GetValueFromMapChecker(@NotNull PsiReferenceExpression mapReference, @NotNull PsiVariable key) {
      this.myMapReference = mapReference;
      this.myKey = key;
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(expression);
      if (parent instanceof PsiAssignmentExpression) {
        if (expression.isReferenceTo(myKey) ||
            EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(myMapReference, expression)) {
          myTainted = true;
          stopWalking();
        }
      }
      if (!expression.isReferenceTo(myKey)) return;
      if (isMapGetArgument(expression)) {
        myGetValueFromMap = true;
      } else {
        myKeyUsedExplicitly = true;
      }
    }

    private boolean isMapGetArgument(PsiReferenceExpression arg) {
      PsiExpressionList argList = tryCast(PsiUtil.skipParenthesizedExprUp(arg.getParent()), PsiExpressionList.class);
      if (argList == null || argList.getExpressionCount() != 1) return false;
      PsiMethodCallExpression call = tryCast(argList.getParent(), PsiMethodCallExpression.class);
      if (!MAP_GET.test(call)) return false;
      PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      return EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(myMapReference, qualifier);
    }
    
    boolean isKeyUsedExplicitly() {
      return myKeyUsedExplicitly;
    }

    boolean isGetValueFromMap() {
      return myGetValueFromMap && !myTainted;
    }
  }
}
