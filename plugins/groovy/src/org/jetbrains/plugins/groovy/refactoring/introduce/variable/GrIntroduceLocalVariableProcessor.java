/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.introduce.variable;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;

import java.util.List;

/**
 * @author Max Medvedev
 */
public abstract class GrIntroduceLocalVariableProcessor {
  private static final Logger LOG = Logger.getInstance(GrIntroduceLocalVariableProcessor.class);

  private final GrIntroduceContext myContext;
  private final GroovyIntroduceVariableSettings mySettings;
  private final boolean myProcessUsages;
  private final PsiElement[] myOccurrences;
  private final GrExpression myExpression;

  public GrIntroduceLocalVariableProcessor(@NotNull GrIntroduceContext context,
                                           @NotNull GroovyIntroduceVariableSettings settings,
                                           @NotNull PsiElement[] occurrences,
                                           @NotNull GrExpression expression,
                                           boolean processUsages) {

    myContext = context;
    mySettings = settings;
    myProcessUsages = processUsages;
    myOccurrences = settings.replaceAllOccurrences() ? occurrences : new PsiElement[]{expression};
    myExpression = expression;
  }

  @NotNull
  public GrVariable processExpression(@NotNull GrVariableDeclaration declaration) {
    resolveLocalConflicts(myContext.getScope(), mySettings.getName());

    preprocessOccurrences();

    int expressionIndex = ArrayUtilRt.find(myOccurrences, myExpression);
    final PsiElement[] replaced = myProcessUsages ? processOccurrences() : myOccurrences;
    PsiElement replacedExpression = replaced[expressionIndex];
    GrStatement anchor = GrIntroduceHandlerBase.getAnchor(replaced, myContext.getScope());

    RefactoringUtil.highlightAllOccurrences(myContext.getProject(), replaced, myContext.getEditor());

    return insertVariableDefinition(declaration, anchor, replacedExpression);
  }

  protected abstract void refreshPositionMarker(PsiElement e);

  private static boolean isControlStatementBranch(GrStatement statement) {
    return statement.getParent() instanceof GrLoopStatement && statement == ((GrLoopStatement)statement.getParent()).getBody() ||
           statement.getParent() instanceof GrIfStatement &&
           (statement == ((GrIfStatement)statement.getParent()).getThenBranch() ||
            statement == ((GrIfStatement)statement.getParent()).getElseBranch());
  }

  private PsiElement[] processOccurrences() {

    List<PsiElement> result = ContainerUtil.newArrayList();

    GrReferenceExpression templateRef =
      GroovyPsiElementFactory.getInstance(myContext.getProject()).createReferenceExpressionFromText(mySettings.getName());
    for (PsiElement occurrence : myOccurrences) {
      if (!(occurrence instanceof GrExpression)) {
        throw new IncorrectOperationException("Expression occurrence to be replaced is not instance of GroovyPsiElement");
      }

      final GrExpression replaced = ((GrExpression)occurrence).replaceWithExpression(templateRef, true);
      result.add(replaced);
    }

    return PsiUtilCore.toPsiElementArray(result);
  }

  @NotNull
  private GrExpression preprocessOccurrences() {
    GroovyRefactoringUtil.sortOccurrences(myOccurrences);
    if (myOccurrences.length == 0 || !(myOccurrences[0] instanceof GrExpression)) {
      throw new IncorrectOperationException("Wrong expression occurrence");
    }

    return (GrExpression)myOccurrences[0];
  }

  private static void resolveLocalConflicts(@NotNull PsiElement tempContainer, @NotNull String varName) {
    for (PsiElement child : tempContainer.getChildren()) {
      if (child instanceof GrReferenceExpression && !child.getText().contains(".")) {
        PsiReference psiReference = child.getReference();
        if (psiReference != null) {
          final PsiElement resolved = psiReference.resolve();
          if (resolved != null) {
            String fieldName = getFieldName(resolved);
            if (fieldName != null && varName.equals(fieldName)) {
              GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(tempContainer.getProject());
              ((GrReferenceExpression)child).replaceWithExpression(factory.createExpressionFromText("this." + child.getText()), true);
            }
          }
        }
      }
      else {
        resolveLocalConflicts(child, varName);
      }
    }
  }

  @NotNull
  private GrVariable insertVariableDefinition(@NotNull GrVariableDeclaration declaration,
                                              @NotNull GrStatement anchor,
                                              @Nullable PsiElement expression) throws IncorrectOperationException {
    GrLabeledStatement labeledStatement = expression != null && expression.getParent() instanceof GrLabeledStatement ? (GrLabeledStatement)expression.getParent() : null;

    boolean expressionMustBeDeleted = expression != null && PsiUtil.isExpressionStatement(expression) && !isSingleGStringInjectionExpr(expression);
    boolean anchorEqualsExpression = anchor == expression || labeledStatement == anchor;

    String usedLabel = labeledStatement != null ? labeledStatement.getName() : null;

    if (expressionMustBeDeleted && !anchorEqualsExpression) {
      expression.delete();
    }

    boolean isInsideControlStatement = isControlStatementBranch(anchor);
    if (isInsideControlStatement) {
      anchor = insertBraces(anchor);
    }

    LOG.assertTrue(myOccurrences.length > 0);

    GrStatementOwner block = (GrStatementOwner)anchor.getParent();

    if (usedLabel != null && expressionMustBeDeleted && anchorEqualsExpression) {
      GrLabeledStatement definitionWithLabel = (GrLabeledStatement)GroovyPsiElementFactory.getInstance(anchor.getProject()).createStatementFromText(usedLabel + ": foo()");
      GrLabeledStatement inserted = insertStatement(definitionWithLabel, anchor, block, true);
      declaration = inserted.getStatement().replaceWithStatement(declaration);
    }
    else {
      declaration = insertStatement(declaration, anchor, block, expressionMustBeDeleted && anchorEqualsExpression);
    }

    final GrVariable variable = declaration.getVariables()[0];
    JavaCodeStyleManager.getInstance(declaration.getProject()).shortenClassReferences(declaration);


    PsiElement markerPlace = expressionMustBeDeleted  ? variable :
                             isInsideControlStatement ? declaration.getParent()
                                                      : expression;
    refreshPositionMarker(markerPlace);

    return variable;
  }

  private static <T extends GrStatement> T insertStatement(T declaration,
                                                           GrStatement anchor,
                                                           GrStatementOwner block,
                                                           boolean replaceAnchor) {
    if (replaceAnchor) {
      return (T)anchor.replace(declaration);
    }
    else {
      return (T)block.addStatementBefore(declaration, anchor);
    }
  }

  @NotNull
  static GrStatement insertBraces(@NotNull GrStatement anchor) {
    GrBlockStatement blockStatement = GroovyPsiElementFactory.getInstance(anchor.getProject()).createBlockStatement();

    blockStatement.getBlock().addStatementBefore(anchor, null);
    GrBlockStatement newBlockStatement = ((GrBlockStatement)anchor.replace(blockStatement));
    return newBlockStatement.getBlock().getStatements()[0];
  }

  private static boolean isSingleGStringInjectionExpr(PsiElement expression) {
    PsiElement parent = expression.getParent();
    return parent instanceof GrClosableBlock && parent.getParent() instanceof GrStringInjection;
  }

  @Nullable
  private static String getFieldName(@Nullable PsiElement element) {
    if (element instanceof GrAccessorMethod) element = ((GrAccessorMethod)element).getProperty();
    return element instanceof GrField ? ((GrField)element).getName() : null;
  }

}
