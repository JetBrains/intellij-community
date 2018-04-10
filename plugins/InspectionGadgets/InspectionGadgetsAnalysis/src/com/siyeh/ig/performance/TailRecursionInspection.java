/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.graph.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class TailRecursionInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("tail.recursion.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("tail.recursion.problem.descriptor");
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiMethod containingMethod = (PsiMethod)infos[0];
    if (!mayBeReplacedByIterativeMethod(containingMethod)) {
      return null;
    }
    return new RemoveTailRecursionFix();
  }

  private static boolean mayBeReplacedByIterativeMethod(PsiMethod containingMethod) {
    if (containingMethod.isVarArgs()) {
      return false;
    }
    final PsiParameter[] parameters = containingMethod.getParameterList().getParameters();
    for (final PsiParameter parameter : parameters) {
      if (parameter.hasModifierProperty(PsiModifier.FINAL)) {
        return false;
      }
    }
    return true;
  }

  private static class RemoveTailRecursionFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("tail.recursion.replace.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement tailCallToken = descriptor.getPsiElement();
      final PsiMethod method =
        PsiTreeUtil.getParentOfType(tailCallToken, PsiMethod.class, true, PsiClass.class, PsiLambdaExpression.class);
      if (method == null) {
        return;
      }
      final PsiCodeBlock body = method.getBody();
      if (body == null) {
        return;
      }
      @NonNls final StringBuilder builder = new StringBuilder();
      builder.append('{');
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final String thisVariableName;
      final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);
      if (methodReturnsContainingClassType(method, containingClass)) {
        builder.append(containingClass.getName());
        thisVariableName = styleManager.suggestUniqueVariableName("result", method, false);
        builder.append(' ').append(thisVariableName).append(" = this;");
      }
      else if (methodContainsCallOnOtherInstance(method)) {
        builder.append(containingClass.getName());
        thisVariableName = styleManager.suggestUniqueVariableName("other", method, false);
        builder.append(' ').append(thisVariableName).append(" = this;");
      }
      else {
        thisVariableName = null;
      }
      final boolean tailCallIsContainedInLoop;
      if (ControlFlowUtils.isInLoop(tailCallToken)) {
        tailCallIsContainedInLoop = true;
        builder.append(method.getName()).append(':');
      }
      else {
        tailCallIsContainedInLoop = false;
      }
      builder.append("while(true)");
      CommentTracker tracker = new CommentTracker();
      replaceTailCalls(body, method, thisVariableName, tailCallIsContainedInLoop, builder, tracker);
      builder.append('}');
      final PsiCodeBlock block = JavaPsiFacade.getElementFactory(project).createCodeBlockFromText(builder.toString(), method);
      tracker.replaceAndRestoreComments(body,block);
      CodeStyleManager.getInstance(project).reformat(method);
    }

    private static boolean methodReturnsContainingClassType(PsiMethod method, PsiClass containingClass) {
      if (containingClass == null) {
        return false;
      }
      if (method.hasModifierProperty(PsiModifier.STATIC)) {
        return false;
      }
      final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(method.getReturnType());
      return containingClass.equals(aClass);
    }

    private static boolean methodContainsCallOnOtherInstance(PsiMethod method) {
      if (method.hasModifierProperty(PsiModifier.STATIC)) {
        return false;
      }
      final PsiCodeBlock body = method.getBody();
      if (body == null) {
        return false;
      }
      final PsiClass aClass = method.getContainingClass();
      final MethodContainsCallOnOtherInstanceVisitor visitor = new MethodContainsCallOnOtherInstanceVisitor(aClass);
      body.accept(visitor);
      return visitor.containsCallOnOtherInstance();
    }

    private static class MethodContainsCallOnOtherInstanceVisitor extends JavaRecursiveElementWalkingVisitor {

      private boolean containsCallOnOtherInstance;
      private final PsiClass aClass;

      MethodContainsCallOnOtherInstanceVisitor(PsiClass aClass) {
        this.aClass = aClass;
      }

      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        if (containsCallOnOtherInstance) {
          return;
        }
        super.visitMethodCallExpression(expression);
        final PsiReferenceExpression methodExpression = expression.getMethodExpression();
        final PsiExpression qualifier = methodExpression.getQualifierExpression();
        if (qualifier == null || qualifier instanceof PsiThisExpression) {
          return;
        }
        final PsiMethod method = expression.resolveMethod();
        if (method == null) {
          return;
        }
        final PsiClass containingClass = method.getContainingClass();
        if (aClass.equals(containingClass)) {
          containsCallOnOtherInstance = true;
        }
      }

      boolean containsCallOnOtherInstance() {
        return containsCallOnOtherInstance;
      }
    }

    private static void replaceTailCalls(PsiElement element,
                                         PsiMethod method,
                                         @Nullable String thisVariableName,
                                         boolean tailCallIsContainedInLoop,
                                         @NonNls StringBuilder out,
                                         CommentTracker tracker) {
      if (isImplicitCallOnThis(element, method)) {
        if (thisVariableName != null) {
          out.append(thisVariableName).append('.');
        }
        out.append(tracker.text(element));
      }
      else if (element instanceof PsiThisExpression || element instanceof PsiSuperExpression) {
        if (thisVariableName == null) {
          out.append(element.getText());
        }
        else {
          out.append(thisVariableName);
        }
      }
      else if (isTailCallReturn(element, method)) {
        final PsiReturnStatement returnStatement = (PsiReturnStatement)element;
        final PsiMethodCallExpression call = (PsiMethodCallExpression)ParenthesesUtils.stripParentheses(returnStatement.getReturnValue());
        assert call != null;
        final PsiExpression[] arguments = call.getArgumentList().getExpressions();
        final PsiParameter[] parameters = method.getParameterList().getParameters();
        final boolean isInBlock = returnStatement.getParent() instanceof PsiCodeBlock;
        if (!isInBlock) {
          out.append('{');
        }
        final Graph<Integer> graph = buildGraph(parameters, arguments);
        // When replacing recursion with iteration, new values are assigned to the parameters,
        // instead of calling the method with the new values. Care needs to be taken to not clobber
        // the value of a parameter which is used later (in some expression assigned to a different
        // parameter). To achieve this a simple graph of the dependencies between the parameters is
        // built and analysed/ordered. If the graph is a directed acyclic graph, the assignments
        // are ordered in such a way that making a defensive copy is unnecessary (topological
        // ordering). If the graph has a cycle, a copy of the value of at least one parameter needs
        // to be made before assigning a new value.
        final DFSTBuilder<Integer> builder = new DFSTBuilder<>(graph);
        final List<Integer> sortedNodes = builder.getSortedNodes();
        final Set<Integer> seen = new HashSet<>();
        final Map<PsiElement, String> replacements = new HashMap<>();
        for (Integer index : sortedNodes) {
          final PsiParameter parameter = parameters[index];
          final String parameterName = parameter.getName();
          assert parameterName != null;
          final PsiExpression argument = ParenthesesUtils.stripParentheses(arguments[index]);
          assert argument != null;
          if (argument instanceof PsiReferenceExpression) {
            final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)argument;
            if (parameter.equals(referenceExpression.resolve())) {
              // parameter keeps same value
              continue;
            }
          }
          final Iterator<Integer> dependants = graph.getIn(index); // parameters which depend on parameter 'index'
          boolean copy = false;
          while (dependants.hasNext()) {
            if (!seen.contains(dependants.next())) {
              // current parameter which depends on value of parameter 'index' has not yet received its value (cycle)
              // if 'dependants' was some collection instead of an iterator this would have been a nice containsAll expression
              copy = true;
              break;
            }
          }
          if (copy) {
            final String variableName =
              JavaCodeStyleManager.getInstance(method.getProject()).suggestUniqueVariableName(parameterName, element, false);
            out.append(parameter.getType().getCanonicalText()).append(' ').append(variableName).append('=');
            out.append(parameterName).append(';');
            replacements.put(parameter, variableName);
          }
          out.append(parameterName).append('=');
          buildText(argument, replacements, out, tracker);
          out.append(';');
          seen.add(index);
        }
        if (thisVariableName != null) {
          final PsiReferenceExpression methodExpression = call.getMethodExpression();
          final PsiExpression qualifier = methodExpression.getQualifierExpression();
          if (qualifier != null) {
            out.append(thisVariableName).append('=');
            replaceTailCalls(qualifier, method, thisVariableName, tailCallIsContainedInLoop, out, tracker);
            out.append(';');
          }
        }
        final PsiCodeBlock body = method.getBody();
        assert body != null;
        if (ControlFlowUtils.blockCompletesWithStatement(body, returnStatement)) {
          //don't do anything, as the continue is unnecessary
        }
        else if (tailCallIsContainedInLoop) {
          out.append("continue ").append(method.getName()).append(';');
        }
        else {
          out.append("continue;");
        }
        if (!isInBlock) {
          out.append('}');
        }
      }
      else {
        final PsiElement[] children = element.getChildren();
        if (children.length == 0) {
          out.append(tracker.text(element));
        }
        else {
          for (final PsiElement child : children) {
            replaceTailCalls(child, method, thisVariableName, tailCallIsContainedInLoop, out, tracker);
          }
        }
      }
    }

    private static void buildText(PsiElement element,
                                  Map<PsiElement, String> replacements,
                                  StringBuilder out,
                                  CommentTracker tracker) {
      if (element instanceof PsiReferenceExpression) {
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)element;
        final PsiElement target = referenceExpression.resolve();
        final String replacement = replacements.get(target);
        out.append(replacement != null ? replacement : tracker.text(element));
        return;
      }
      final PsiElement[] children = element.getChildren();
      if (children.length > 0) {
        for (PsiElement child : children) {
          buildText(child, replacements, out, tracker);
        }
      }
      else {
        out.append(tracker.text(element));
      }
    }

    private static Graph<Integer> buildGraph(PsiParameter[] parameters, PsiExpression[] arguments) {
      final InboundSemiGraph<Integer> graph = new InboundSemiGraph<Integer>() {
        @Override
        public Collection<Integer> getNodes() {
          final List<Integer> result = new ArrayList<>();
          for (int i = 0; i < parameters.length; i++) {
            result.add(i);
          }
          return result;
        }

        @Override
        public Iterator<Integer> getIn(Integer n) {
          final List<Integer> result = new ArrayList<>();
          final PsiParameter target = parameters[n];
          for (int i = 0, length = arguments.length; i < length; i++) {
            if (i == n) continue;
            if (VariableAccessUtils.variableIsUsed(target, arguments[i])) {
              result.add(i);
            }
          }
          return result.iterator();
        }
      };
      return GraphGenerator.generate(CachingSemiGraph.cache(graph));
    }

    private static boolean isImplicitCallOnThis(PsiElement element, PsiMethod containingMethod) {
      if (containingMethod.hasModifierProperty(PsiModifier.STATIC)) {
        return false;
      }
      if (element instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element;
        final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
        return qualifierExpression == null;
      }
      else if (element instanceof PsiReferenceExpression) {
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)element;
        final PsiElement parent = referenceExpression.getParent();
        if (parent instanceof PsiMethodCallExpression) {
          return false;
        }
        final PsiExpression qualifier = referenceExpression.getQualifierExpression();
        if (qualifier != null) {
          return false;
        }
        final PsiElement target = referenceExpression.resolve();
        return target instanceof PsiField;
      }
      else {
        return false;
      }
    }

    private static boolean isTailCallReturn(PsiElement element, PsiMethod containingMethod) {
      if (!(element instanceof PsiReturnStatement)) {
        return false;
      }
      final PsiReturnStatement returnStatement = (PsiReturnStatement)element;
      final PsiExpression returnValue = ParenthesesUtils.stripParentheses(returnStatement.getReturnValue());
      if (!(returnValue instanceof PsiMethodCallExpression)) {
        return false;
      }
      final PsiMethodCallExpression call = (PsiMethodCallExpression)returnValue;
      final PsiMethod method = call.resolveMethod();
      return containingMethod.equals(method);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new TailRecursionVisitor();
  }

  private static class TailRecursionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
      super.visitReturnStatement(statement);
      final PsiExpression returnValue = ParenthesesUtils.stripParentheses(statement.getReturnValue());
      if (!(returnValue instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiMethodCallExpression returnCall = (PsiMethodCallExpression)returnValue;
      final PsiReferenceExpression methodExpression = returnCall.getMethodExpression();
      final PsiMethod containingMethod =
        PsiTreeUtil.getParentOfType(statement, PsiMethod.class, true, PsiClass.class, PsiLambdaExpression.class);
      if (containingMethod == null) {
        return;
      }
      final JavaResolveResult resolveResult = returnCall.resolveMethodGenerics();
      if (!resolveResult.isValidResult() || !containingMethod.equals(resolveResult.getElement())) {
        return;
      }
      final PsiExpression qualifier = ParenthesesUtils.stripParentheses(methodExpression.getQualifierExpression());
      if (qualifier != null && !(qualifier instanceof PsiThisExpression) && MethodUtils.isOverridden(containingMethod)) {
        return;
      }
      registerMethodCallError(returnCall, containingMethod);
    }
  }
}