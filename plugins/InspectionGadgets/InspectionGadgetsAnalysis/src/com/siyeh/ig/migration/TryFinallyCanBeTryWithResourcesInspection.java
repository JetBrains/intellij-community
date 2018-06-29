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
package com.siyeh.ig.migration;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.*;
import gnu.trove.TIntArrayList;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.util.ObjectUtils.tryCast;

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
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
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
      PsiCodeBlock tryBlock = tryStatement.getTryBlock();
      if (tryBlock == null) return;
      Context context = Context.from(tryStatement);
      if (context == null) return;
      String resourceList = context.myResourceVariables.stream()
                                                       .map(ResourceVariable::generateResourceDeclaration)
                                                       .collect(Collectors.joining(";"));
      StringBuilder sb = new StringBuilder("try(");
      sb.append(resourceList);
      PsiResourceList resourceListElement = tryStatement.getResourceList();
      if (resourceListElement != null) {
        PsiElement[] children = resourceListElement.getChildren();
        if (children.length > 2 && resourceListElement.getResourceVariablesCount() > 0) {
          sb.append(";");
          for (int i = 1; i < children.length - 1; i++) {
            sb.append(children[i].getText());
          }
        }
      }
      sb.append(")");
      List<PsiLocalVariable> locals = StreamEx.of(context.myResourceVariables)
                                              .map(resourceVariable -> resourceVariable.myVariable)
                                              .select(PsiLocalVariable.class)
                                              .sorted(PsiElementOrderComparator.getInstance())
                                              .toList();

      if (locals.size() == 1) {
        PsiLocalVariable variable = locals.get(0);
        PsiStatement declaration = PsiTreeUtil.getParentOfType(variable, PsiStatement.class);
        if (declaration == null) return;
        if (declaration.getParent() != tryStatement.getParent()) return;
        List<PsiStatement> statements = collectStatementsBetween(declaration, tryStatement);
        PsiJavaToken lBrace = tryBlock.getLBrace();
        if (lBrace != null) {
          for (int i = statements.size() - 1; i >= 0; i--) {
            PsiStatement statement = statements.get(i);
            tryBlock.addAfter(statement, lBrace);
            if (statement.isValid()) {
              statement.delete();
            }
          }
        }
      }
      restoreStatementsBeforeLastVariableInTryResource(tryStatement, tryBlock, context);


      for (PsiStatement statement : context.myStatementsToDelete) {
        if (statement.isValid()) {
          new CommentTracker().deleteAndRestoreComments(statement);
        }
      }
      for (ResourceVariable variable : context.myResourceVariables) {
        if (!variable.myUsedOutsideTry) {
          if (variable.myVariable.isValid()) {
            new CommentTracker().deleteAndRestoreComments(variable.myVariable);
          }
        }
      }
      sb.append(tryBlock.getText());
      for (PsiCatchSection section : tryStatement.getCatchSections()) {
        sb.append(section.getText());
      }
      PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
      if (finallyBlock == null) return;
      if (!ControlFlowUtils.isEmptyCodeBlock(finallyBlock)) {
        sb.append("finally").append(finallyBlock.getText());
      }
      else {
        PsiElement[] finallyBlockChildren = finallyBlock.getChildren();
        if (!StreamEx.of(finallyBlockChildren).skip(1).limit(finallyBlockChildren.length - 2).allMatch(el -> el instanceof PsiWhiteSpace)) {
          PsiElement tryParent = tryStatement.getParent();
          tryParent.addRangeAfter(finallyBlockChildren[1], finallyBlockChildren[finallyBlockChildren.length - 2], tryStatement);
        }
      }
      tryStatement.replace(JavaPsiFacade.getElementFactory(project).createStatementFromText(sb.toString(), tryStatement));
    }


    private static void restoreStatementsBeforeLastVariableInTryResource(PsiTryStatement tryStatement,
                                                                         PsiCodeBlock tryBlock,
                                                                         Context context) {
      Optional<PsiExpression> lastInTryVariable = StreamEx.of(context.myResourceVariables)
                                                          .map(v -> v.myInitializer)
                                                          .filter(e -> e != null && PsiTreeUtil.isAncestor(tryBlock, e, false))
                                                          .max(PsiElementOrderComparator.getInstance());
      List<PsiStatement> elementsToRestore = new ArrayList<>();
      if (lastInTryVariable.isPresent()) {
        PsiStatement last = PsiTreeUtil.getParentOfType(lastInTryVariable.get(), PsiStatement.class);
        PsiStatement[] statements = tryBlock.getStatements();
        for (int i = 0; i < statements.length && statements[i] != last; i++) {
          PsiStatement current = statements[i];
          if (context.myStatementsToDelete.contains(current)) {
            continue;
          }
          elementsToRestore.add(current);
        }
      }
      PsiElement tryStatementParent = tryStatement.getParent();
      for (PsiStatement statement : elementsToRestore) {
        tryStatementParent.addBefore(statement, tryStatement);
        statement.delete();
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
      if (Context.from(tryStatement) == null) return;
      registerStatementError(tryStatement);
    }
  }

  private static class Context {
    final @NotNull List<ResourceVariable> myResourceVariables;
    final @NotNull Set<PsiStatement> myStatementsToDelete;

    private Context(@NotNull List<ResourceVariable> resourceVariables, @NotNull Set<PsiStatement> statementsToDelete) {
      myResourceVariables = resourceVariables;
      myStatementsToDelete = statementsToDelete;
    }

    static @Nullable
    Context from(@NotNull PsiTryStatement tryStatement) {
      PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
      if (finallyBlock == null) return null;
      PsiCodeBlock tryBlock = tryStatement.getTryBlock();
      if (tryBlock == null) return null;
      PsiStatement[] tryStatements = tryBlock.getStatements();
      PsiStatement[] finallyStatements = finallyBlock.getStatements();
      BitSet closedVariableStatementIndices = new BitSet(finallyStatements.length);
      Set<PsiVariable> collectedVariables = new HashSet<>();
      for (int i = 0, length = finallyStatements.length; i < length; i++) {
        PsiStatement statement = finallyStatements[i];
        closedVariableStatementIndices.set(i, findAutoCloseableVariables(statement, collectedVariables));
      }
      if (collectedVariables.isEmpty()) return null;
      if (resourceVariablesUsedInFinally(finallyStatements, closedVariableStatementIndices, collectedVariables)) return null;
      if (resourceVariableUsedInCatches(tryStatement, collectedVariables)) return null;

      List<ResourceVariable> resourceVariables = new ArrayList<>();
      List<PsiStatement> statementsToDelete = new ArrayList<>();
      TIntArrayList initializerPositions = new TIntArrayList();
      for (PsiVariable resourceVariable : collectedVariables) {
        boolean variableUsedOutsideTry = isVariableUsedOutsideContext(resourceVariable, tryStatement);
        if (!PsiUtil.isLanguageLevel9OrHigher(finallyBlock) && variableUsedOutsideTry) return null;
        if (!variableUsedOutsideTry && resourceVariable instanceof PsiLocalVariable) {
          PsiExpression initializer = resourceVariable.getInitializer();
          boolean hasNonNullInitializer = initializer != null && !PsiType.NULL.equals(initializer.getType());
          if (!hasNonNullInitializer) {
            int assignmentStatementIndex = findInitialization(tryStatements, resourceVariable);
            if (assignmentStatementIndex == -1) return null;
            initializerPositions.add(assignmentStatementIndex);
            PsiExpressionStatement assignmentStatement = (PsiExpressionStatement)tryStatements[assignmentStatementIndex];
            PsiExpression expression = assignmentStatement.getExpression();
            PsiAssignmentExpression assignment = tryCast(expression, PsiAssignmentExpression.class);
            if (assignment == null) return null;
            initializer = assignment.getRExpression();
            if (initializer == null) return null;
            statementsToDelete.add(tryStatements[assignmentStatementIndex]);
          }
          else {
            if (VariableAccessUtils.variableIsAssigned(resourceVariable, tryBlock)) return null;
          }
          resourceVariables.add(new ResourceVariable(initializer, false, resourceVariable));
        }
        else if (((resourceVariable instanceof PsiLocalVariable && resourceVariable.getInitializer() != null) ||
                  resourceVariable instanceof PsiParameter) && FinalUtils.canBeFinal(resourceVariable)) {
          resourceVariables.add(new ResourceVariable(null, true, resourceVariable));
        }
        else {
          return null;
        }
      }
      for (int i = 0; i < finallyStatements.length; i++) {
        if (closedVariableStatementIndices.get(i)) {
          statementsToDelete.add(finallyStatements[i]);
        }
      }
      if (!noStatementsBetweenVariableDeclarations(collectedVariables)) return null;
      if (!initializersAreAtTheBeginning(initializerPositions)) return null;

      Collections.sort(resourceVariables, Comparator.comparing(o -> o.myVariable, PsiElementOrderComparator.getInstance()));
      return new Context(resourceVariables, new HashSet<>(statementsToDelete));
    }

    private static boolean initializersAreAtTheBeginning(TIntArrayList initializerPositions) {
      initializerPositions.sort();
      for (int i = 0; i < initializerPositions.size(); i++) {
        if (initializerPositions.get(i) != i) return false;
      }
      return true;
    }

    private static boolean noStatementsBetweenVariableDeclarations(Set<PsiVariable> collectedVariables) {
      return StreamEx.of(collectedVariables)
                     .select(PsiLocalVariable.class)
                     .sorted(PsiElementOrderComparator.getInstance())
                     .map(var -> PsiTreeUtil.getParentOfType(var, PsiStatement.class))
                     .pairMap((l1, l2) -> l1 != null && l2 != null && l1.getParent() == l2.getParent() && collectStatementsBetween(l1, l2).isEmpty())
                     .allMatch(b -> b);
    }
  }

  private static List<PsiStatement> collectStatementsBetween(PsiStatement startExclusive, PsiStatement endExclusive) {
    List<PsiStatement> statements = new ArrayList<>();
    PsiStatement current = PsiTreeUtil.getNextSiblingOfType(startExclusive, PsiStatement.class);
    while (current != endExclusive && current != null) {
      statements.add(current);
      current = PsiTreeUtil.getNextSiblingOfType(current.getNextSibling(), PsiStatement.class);
    }
    return statements;
  }

  private static boolean resourceVariablesUsedInFinally(PsiStatement[] statements,
                                                        BitSet closedVariableStatementIndices,
                                                        Set<PsiVariable> resourceVariables) {
    for (int i = 0; i < statements.length; i++) {
      if (!closedVariableStatementIndices.get(i)) {
        Set<PsiVariable> usedVariables = VariableAccessUtils.collectUsedVariables(statements[i]);
        for (PsiVariable usedVariable : usedVariables) {
          if (resourceVariables.contains(usedVariable)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static boolean resourceVariableUsedInCatches(PsiTryStatement tryStatement, Set<PsiVariable> collectedVariables) {
    for (PsiCatchSection catchSection : tryStatement.getCatchSections()) {
      for (PsiVariable variable : collectedVariables) {
        if (VariableAccessUtils.variableIsUsed(variable, catchSection)) {
          return true;
        }
      }
    }
    return false;
  }

  private static class ResourceVariable {
    final @Nullable("when in java 9") PsiExpression myInitializer;
    final boolean myUsedOutsideTry; // true only if Java9 or above
    final @NotNull PsiVariable myVariable;

    ResourceVariable(@Nullable PsiExpression initializer, boolean usedOutsideTry, @NotNull PsiVariable variable) {
      myInitializer = initializer;
      myUsedOutsideTry = usedOutsideTry;
      myVariable = variable;
    }

    String generateResourceDeclaration() {
      if (myUsedOutsideTry) {
        return myVariable.getName();
      }
      else {
        assert myInitializer != null;
        return Objects.requireNonNull(myVariable.getTypeElement()).getText() + " " + myVariable.getName() + "=" + myInitializer.getText();
      }
    }
  }

  private static boolean isVariableUsedOutsideContext(PsiVariable variable, PsiElement context) {
    final VariableUsedOutsideContextVisitor visitor = new VariableUsedOutsideContextVisitor(variable, context);
    final PsiElement declarationScope = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
    if (declarationScope == null) {
      return true;
    }
    declarationScope.accept(visitor);
    return visitor.variableIsUsed();
  }

  private static boolean findAutoClosableVariableWithoutTry(PsiStatement statement, Set<PsiVariable> variables) {
    if (statement instanceof PsiIfStatement) {
      final PsiIfStatement ifStatement = (PsiIfStatement)statement;
      if (ifStatement.getElseBranch() != null) return false;
      final PsiExpression condition = ifStatement.getCondition();
      if (!(condition instanceof PsiBinaryExpression)) return false;
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)condition;
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      if (!JavaTokenType.NE.equals(tokenType)) return false;
      final PsiExpression lhs = binaryExpression.getLOperand();
      final PsiExpression rhs = binaryExpression.getROperand();
      if (rhs == null) return false;
      final PsiElement variable;
      if (PsiType.NULL.equals(rhs.getType())) {
        variable = ExpressionUtils.resolveLocalVariable(lhs);
      }
      else if (PsiType.NULL.equals(lhs.getType())) {
        variable = ExpressionUtils.resolveLocalVariable(rhs);
      }
      else {
        return false;
      }
      if (variable == null) return false;
      final PsiStatement thenBranch = ifStatement.getThenBranch();
      final PsiVariable resourceVariable;
      if (thenBranch instanceof PsiExpressionStatement) {
        resourceVariable = findAutoCloseableVariable(thenBranch);
      }
      else if (thenBranch instanceof PsiBlockStatement) {
        final PsiBlockStatement blockStatement = (PsiBlockStatement)thenBranch;
        final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
        resourceVariable = findAutoCloseableVariable(ControlFlowUtils.getOnlyStatementInBlock(codeBlock));
      }
      else {
        return false;
      }
      if (variable.equals(resourceVariable)) {
        variables.add(resourceVariable);
        return true;
      }
    }
    else if (statement instanceof PsiExpressionStatement) {
      final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)statement;
      final PsiExpression expression = expressionStatement.getExpression();
      if (!(expression instanceof PsiMethodCallExpression)) return false;
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      if (HardcodedMethodConstants.CLOSE.equals(methodName)) {
        final PsiExpression qualifier = methodExpression.getQualifierExpression();
        if (!(qualifier instanceof PsiReferenceExpression)) return false;
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifier;
        final PsiElement target = referenceExpression.resolve();
        if (!(target instanceof PsiLocalVariable || target instanceof PsiParameter) || target instanceof PsiResourceVariable) return false;
        PsiVariable variable = (PsiVariable)target;
        if (!isAutoCloseable(variable)) return false;
        variables.add(variable);
        return true;
      }
      else {
        return false;
      }
    }
    return false;
  }

  @Nullable
  private static PsiVariable findAutoCloseableVariable(PsiStatement statement) {
    Set<PsiVariable> variables = new HashSet<>(1);
    findAutoCloseableVariables(statement, variables);
    if (variables.isEmpty()) {
      return null;
    }
    else {
      return ContainerUtil.getFirstItem(variables);
    }
  }

  private static boolean findAutoCloseableVariables(PsiStatement statement, Set<PsiVariable> variables) {
    if (findAutoClosableVariableWithoutTry(statement, variables)) return true;
    if (statement instanceof PsiTryStatement) {
      PsiTryStatement tryStatement = (PsiTryStatement)statement;
      if (tryStatement.getResourceList() != null || tryStatement.getFinallyBlock() != null) return true;
      PsiCodeBlock[] catchBlocks = tryStatement.getCatchBlocks();
      if (catchBlocks.length != 1) return true;
      PsiStatement[] catchStatements = catchBlocks[0].getStatements();
      if (catchStatements.length != 0) return true;
      PsiCodeBlock tryBlock = tryStatement.getTryBlock();
      if (tryBlock == null) return true;
      PsiStatement[] tryStatements = tryBlock.getStatements();
      boolean containsClosedVariables = false;
      for (PsiStatement tryStmt : tryStatements) {
        if (findAutoClosableVariableWithoutTry(tryStmt, variables)) {
          containsClosedVariables = true;
        }
      }
      return containsClosedVariables;
    }
    return false;
  }

  private static boolean isAutoCloseable(PsiVariable variable) {
    final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(variable.getType());
    return InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE);
  }

  private static int findInitialization(PsiElement[] elements, PsiVariable variable) {
    int result = -1;
    final int statementsLength = elements.length;
    for (int i = 0; i < statementsLength; i++) {
      final PsiElement element = elements[i];
      if (isAssignmentToVariable(element, variable)) {
        if (result >= 0) {
          return -1;
        }
        result = i;
      }
      else if (VariableAccessUtils.variableIsAssigned(variable, element)) {
        return -1;
      }
    }
    return result;
  }

  private static boolean isAssignmentToVariable(PsiElement element, PsiVariable variable) {
    PsiExpressionStatement expressionStatement = tryCast(element, PsiExpressionStatement.class);
    if (expressionStatement == null) return false;
    PsiAssignmentExpression assignmentExpression = tryCast(expressionStatement.getExpression(), PsiAssignmentExpression.class);
    if (assignmentExpression == null) return false;
    if (assignmentExpression.getRExpression() == null) return false;
    return ExpressionUtils.isReferenceTo(assignmentExpression.getLExpression(), variable);
  }

  private static class VariableUsedOutsideContextVisitor extends JavaRecursiveElementWalkingVisitor {

    private boolean used;
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
      if (!argumentList.isEmpty()) {
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
}
