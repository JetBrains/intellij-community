/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.siyeh.ig.migration;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.FinalUtils;
import com.siyeh.ig.psiutils.PsiElementOrderComparator;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Bas Leijdekkers
 */
public class TryFinallyCanBeTryWithResourcesInspection extends BaseInspection {

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("try.finally.can.be.try.with.resources.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("try.finally.can.be.try.with.resources.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new TryFinallyCanBeTryWithResourcesFix();
  }

  private static class TryFinallyCanBeTryWithResourcesFix extends InspectionGadgetsFix {

    public TryFinallyCanBeTryWithResourcesFix() {}
     @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("try.finally.can.be.try.with.resources.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiTryStatement)) {
        return;
      }
      final PsiTryStatement tryStatement = (PsiTryStatement)parent;
      final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
      if (tryBlock == null) {
        return;
      }
      final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
      if (finallyBlock == null) {
        return;
      }
      final PsiElement[] tryBlockChildren = tryBlock.getChildren();
      final Set<PsiLocalVariable> variables = new LinkedHashSet<PsiLocalVariable>();
      for (final PsiLocalVariable variable : collectVariables(tryStatement)) {
        if (!isVariableUsedOutsideContext(variable, tryBlock)) {
          variables.add(variable);
        }
      }
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      @NonNls final StringBuilder newTryStatementText = new StringBuilder("try (");
      final Set<Integer> unwantedChildren = new HashSet(2);
      boolean separator = false;
      for (final PsiLocalVariable variable : variables) {
        final boolean hasInitializer;
        final PsiExpression initializer = variable.getInitializer();
        if (initializer == null) {
          hasInitializer = false;
        }
        else {
          final PsiType type = initializer.getType();
          hasInitializer = !PsiType.NULL.equals(type);
        }
        if (separator) {
          newTryStatementText.append(';');
        }
        newTryStatementText.append(variable.getTypeElement().getText()).append(' ').append(variable.getName()).append('=');
        if (hasInitializer) {
          newTryStatementText.append(initializer.getText());
        }
        else {
          final int index = findInitialization(tryBlockChildren, variable, false);
          if (index < 0) {
            return;
          }
          unwantedChildren.add(Integer.valueOf(index));
          final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)tryBlockChildren[index];
          if (expressionStatement.getNextSibling() instanceof PsiWhiteSpace) {
            unwantedChildren.add(Integer.valueOf(index + 1));
          }
          final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expressionStatement.getExpression();
          final PsiExpression rhs = assignmentExpression.getRExpression();
          if (rhs == null) {
            return;
          }
          newTryStatementText.append(rhs.getText());
        }
        separator = true;
      }
      newTryStatementText.append(") {");
      final int tryBlockStatementsLength = tryBlockChildren.length - 1;
      for (int i = 1; i < tryBlockStatementsLength; i++) {
        final PsiElement child = tryBlockChildren[i];
        if (unwantedChildren.contains(Integer.valueOf(i))) {
          continue;
        }
        newTryStatementText.append(child.getText());
      }
      newTryStatementText.append('}');
      final PsiCatchSection[] catchSections = tryStatement.getCatchSections();
      for (final PsiCatchSection catchSection : catchSections) {
        newTryStatementText.append(catchSection.getText());
      }
      final PsiElement[] finallyChildren = finallyBlock.getChildren();
      boolean appended = false;
      final int finallyChildrenLength = finallyChildren.length - 1;
      final List<PsiElement> savedComments = new ArrayList();
      for (int i = 1; i < finallyChildrenLength; i++) {
        final PsiElement child = finallyChildren[i];
        if (isCloseStatement(child, variables)) {
          continue;
        }
        if (!appended) {
          if (child instanceof PsiComment) {
            final PsiComment comment = (PsiComment)child;
            final PsiElement prevSibling = child.getPrevSibling();
            if (prevSibling instanceof PsiWhiteSpace && savedComments.isEmpty()) {
              savedComments.add(prevSibling);
            }
            savedComments.add(comment);
            final PsiElement nextSibling = child.getNextSibling();
            if (nextSibling instanceof PsiWhiteSpace) {
              savedComments.add(nextSibling);
            }
          }
          else if (!(child instanceof PsiWhiteSpace)) {
            newTryStatementText.append(" finally {");
            for (final PsiElement savedComment : savedComments) {
              newTryStatementText.append(savedComment.getText());
            }
            newTryStatementText.append(child.getText());
            appended = true;
          }
        }
        else {
          newTryStatementText.append(child.getText());
        }
      }
      if (appended) {
        newTryStatementText.append('}');
      }
      for (final PsiLocalVariable variable : variables) {
        variable.delete();
      }
      if (!appended) {
        final int savedCommentsSize = savedComments.size();
        final PsiElement parent1 = tryStatement.getParent();
        for (int i = savedCommentsSize - 1; i >= 0; i--) {
          final PsiElement savedComment = savedComments.get(i);
          parent1.addAfter(savedComment, tryStatement);
        }
      }
      final PsiStatement newTryStatement = factory.createStatementFromText(newTryStatementText.toString(), element);
      tryStatement.replace(newTryStatement);
    }

    private static boolean isCloseStatement(PsiElement element, Set<PsiLocalVariable> variables) {
      if (element instanceof PsiExpressionStatement) {
        final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)element;
        final PsiExpression expression = expressionStatement.getExpression();
        if (!(expression instanceof PsiMethodCallExpression)) {
          return false;
        }
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
        final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        final String methodName = methodExpression.getReferenceName();
        if (!HardcodedMethodConstants.CLOSE.equals(methodName)) {
          return false;
        }
        final PsiExpression qualifier = methodExpression.getQualifierExpression();
        if (!(qualifier instanceof PsiReferenceExpression)) {
          return false;
        }
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifier;
        final PsiElement target = referenceExpression.resolve();
        if (!(target instanceof PsiLocalVariable)) {
          return false;
        }
        final PsiLocalVariable variable = (PsiLocalVariable)target;
        return variables.contains(variable);
      }
      else if (element instanceof PsiIfStatement) {
        final PsiIfStatement ifStatement = (PsiIfStatement)element;
        if (ifStatement.getElseBranch() != null) {
          return false;
        }
        final PsiExpression condition = ifStatement.getCondition();
        if (!(condition instanceof PsiBinaryExpression)) {
          return false;
        }
        final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)condition;
        final IElementType tokenType = binaryExpression.getOperationTokenType();
        if (!JavaTokenType.NE.equals(tokenType)) {
          return false;
        }
        final PsiExpression lhs = binaryExpression.getLOperand();
        final PsiExpression rhs = binaryExpression.getROperand();
        if (rhs == null) {
          return false;
        }
        if (PsiType.NULL.equals(rhs.getType())) {
          if (resolveLocalVariable(lhs) == null) return false;
        }
        else if (PsiType.NULL.equals(lhs.getType())) {
          if (resolveLocalVariable(rhs) == null) return false;
        }
        else {
          return false;
        }
        final PsiStatement thenBranch = ifStatement.getThenBranch();
        if (thenBranch instanceof PsiExpressionStatement) {
          return isCloseStatement(thenBranch, variables);
        }
        else if (thenBranch instanceof PsiBlockStatement) {
          final PsiBlockStatement blockStatement = (PsiBlockStatement)thenBranch;
          final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
          final PsiStatement[] statements = codeBlock.getStatements();
          return statements.length == 1 && isCloseStatement(statements[0], variables);
        }
        else {
          return false;
        }
      }
      else {
        return false;
      }
    }
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    return PsiUtil.isLanguageLevel7OrHigher(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new TryFinallyCanBeTryWithResourcesVisitor();
  }

  private static class TryFinallyCanBeTryWithResourcesVisitor extends BaseInspectionVisitor {

    @Override
    public void visitTryStatement(PsiTryStatement tryStatement) {
      super.visitTryStatement(tryStatement);
      final PsiResourceList resourceList = tryStatement.getResourceList();
      if (resourceList != null) {
        return;
      }
      final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
      if (tryBlock == null) {
        return;
      }
      final List<PsiLocalVariable> variables = collectVariables(tryStatement);
      if (variables.isEmpty()) {
        return;
      }
      final PsiStatement[] tryBlockStatements = tryBlock.getStatements();
      boolean found = false;
      for (PsiVariable variable : variables) {
        final boolean hasInitializer;
        final PsiExpression initializer = variable.getInitializer();
        if (initializer == null) {
          hasInitializer = false;
        }
        else {
          final PsiType type = initializer.getType();
          hasInitializer = !PsiType.NULL.equals(type);
        }
        final int index = findInitialization(tryBlockStatements, variable, hasInitializer);
        if (index >= 0 ^ hasInitializer) {
          if (isVariableUsedOutsideContext(variable, tryBlock)) {
            continue;
          }
          found = true;
          break;
        }
      }
      if (!found) {
        return;
      }
      registerStatementError(tryStatement);
    }
  }

  static boolean isVariableUsedOutsideContext(PsiVariable variable, PsiElement context) {
    if (!FinalUtils.canBeFinal(variable)) {
      return true;
    }
    final VariableUsedOutsideContextVisitor visitor = new VariableUsedOutsideContextVisitor(variable, context);
    final PsiElement declarationScope = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
    if (declarationScope == null) {
      return true;
    }
    declarationScope.accept(visitor);
    return visitor.variableIsUsed();
  }

  static List<PsiLocalVariable> collectVariables(PsiTryStatement tryStatement) {
    final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
    if (finallyBlock == null) {
      return Collections.EMPTY_LIST;
    }
    final PsiStatement[] statements = finallyBlock.getStatements();
    if (statements.length == 0) {
      return Collections.EMPTY_LIST;
    }
    final List<PsiLocalVariable> variables = new ArrayList();
    for (PsiStatement statement : statements) {
      final PsiLocalVariable variable = findAutoCloseableVariable(statement);
      if (variable != null) {
        variables.add(variable);
      }
    }
    Collections.sort(variables, PsiElementOrderComparator.getInstance());
    return variables;
  }

  @Nullable
  static PsiLocalVariable findAutoCloseableVariable(PsiStatement statement) {
    if (statement instanceof PsiIfStatement) {
      final PsiIfStatement ifStatement = (PsiIfStatement)statement;
      if (ifStatement.getElseBranch() != null) {
        return null;
      }
      final PsiExpression condition = ifStatement.getCondition();
      if (!(condition instanceof PsiBinaryExpression)) {
        return null;
      }
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)condition;
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      if (!JavaTokenType.NE.equals(tokenType)) {
        return null;
      }
      final PsiExpression lhs = binaryExpression.getLOperand();
      final PsiExpression rhs = binaryExpression.getROperand();
      if (rhs == null) {
        return null;
      }
      final PsiElement variable;
      if (PsiType.NULL.equals(rhs.getType())) {
        variable = resolveLocalVariable(lhs);
      }
      else if (PsiType.NULL.equals(lhs.getType())) {
        variable = resolveLocalVariable(rhs);
      }
      else {
        return null;
      }
      if (variable == null) {
        return null;
      }
      final PsiStatement thenBranch = ifStatement.getThenBranch();
      final PsiLocalVariable resourceVariable;
      if (thenBranch instanceof PsiExpressionStatement) {
        resourceVariable = findAutoCloseableVariable(thenBranch);
      }
      else if (thenBranch instanceof PsiBlockStatement) {
        final PsiBlockStatement blockStatement = (PsiBlockStatement)thenBranch;
        final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
        final PsiStatement[] statements = codeBlock.getStatements();
        if (statements.length != 1) {
          return null;
        }
        resourceVariable = findAutoCloseableVariable(statements[0]);
      }
      else {
        return null;
      }
      if (variable.equals(resourceVariable)) {
        return resourceVariable;
      }
    }
    else if (statement instanceof PsiExpressionStatement) {
      final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)statement;
      final PsiExpression expression = expressionStatement.getExpression();
      if (!(expression instanceof PsiMethodCallExpression)) {
        return null;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.CLOSE.equals(methodName)) {
        return null;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (!(qualifier instanceof PsiReferenceExpression)) {
        return null;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifier;
      final PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiLocalVariable) || target instanceof PsiResourceVariable) {
        return null;
      }
      final PsiLocalVariable variable = (PsiLocalVariable)target;
      if (!isAutoCloseable(variable)) {
        return null;
      }
      return variable;
    }
    return null;
  }

  private static boolean isAutoCloseable(PsiVariable variable) {
    final PsiType type = variable.getType();
    if (!(type instanceof PsiClassType)) {
      return false;
    }
    final PsiClassType classType = (PsiClassType)type;
    final PsiClass aClass = classType.resolve();
    return aClass != null && InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE);
  }

  static int findInitialization(PsiElement[] elements, PsiVariable variable,
    boolean hasInitializer) {
    int result = -1;
    final int statementsLength = elements.length;
    for (int i = 0; i < statementsLength; i++) {
      final PsiElement element = elements[i];
      if (!(element instanceof PsiExpressionStatement)) {
        continue;
      }
      final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)element;
      final PsiExpression expression = expressionStatement.getExpression();
      if (!(expression instanceof PsiAssignmentExpression)) {
        continue;
      }
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression;
      final PsiExpression lhs = assignmentExpression.getLExpression();
      if (!(lhs instanceof PsiReferenceExpression)) {
        continue;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)lhs;
      final PsiElement target = referenceExpression.resolve();
      if (variable.equals(target)) {
        if (result >= 0 && !hasInitializer) {
          return -1;
        }
        result = i;
      }
    }
    return result;
  }

  static class VariableUsedOutsideContextVisitor extends JavaRecursiveElementVisitor {

    private boolean used = false;
    @NotNull private final PsiVariable variable;
    private final PsiElement skipContext;

    public VariableUsedOutsideContextVisitor(@NotNull PsiVariable variable, PsiElement skipContext) {
      this.variable = variable;
      this.skipContext = skipContext;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      if (element.equals(skipContext)) {
        return;
      }
      if (used) {
        return;
      }
      super.visitElement(element);
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression referenceExpression) {
      if (used) {
        return;
      }
      super.visitReferenceExpression(referenceExpression);
      final PsiElement target = referenceExpression.resolve();
      if (target == null) {
        return;
      }
      if (target.equals(variable) && !isCloseMethodCalled(referenceExpression)) {
        used = true;
      }
    }

    private static boolean isCloseMethodCalled(PsiReferenceExpression referenceExpression) {
      final PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(referenceExpression, PsiMethodCallExpression.class);
      if (methodCallExpression == null) {
        return false;
      }
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      if (argumentList.getExpressions().length != 0) {
        return false;
      }
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final String name = methodExpression.getReferenceName();
      return HardcodedMethodConstants.CLOSE.equals(name);
    }

    public boolean variableIsUsed() {
      return used;
    }
  }

  private static PsiLocalVariable resolveLocalVariable(PsiExpression expression) {
    if (!(expression instanceof PsiReferenceExpression)) {
      return null;
    }
    final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
    final PsiElement target = referenceExpression.resolve();
    return !(target instanceof PsiLocalVariable) ? null : (PsiLocalVariable)target;
  }
}
