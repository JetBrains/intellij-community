/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceRefactoringError;

import java.util.ArrayList;

/**
 * @author ilyas
 */
public abstract class GroovyIntroduceVariableBase extends GrIntroduceHandlerBase<GroovyIntroduceVariableSettings> {

  private static final Logger LOG =
    Logger.getInstance("org.jetbrains.plugins.groovy.refactoring.introduceVariable.groovyIntroduceVariableBase");
  protected static String REFACTORING_NAME = GroovyRefactoringBundle.message("introduce.variable.title");
  private PsiElement positionElement = null;

  @NotNull
  @Override
  protected PsiElement findScope(GrExpression selectedExpr, GrVariable variable) {

    // Get container element
    final PsiElement scope = GroovyRefactoringUtil.getEnclosingContainer(selectedExpr);
    if (scope == null || !(scope instanceof GroovyPsiElement)) {
      throw new GrIntroduceRefactoringError(
        GroovyRefactoringBundle.message("refactoring.is.not.supported.in.the.current.context", REFACTORING_NAME));
    }
    if (!GroovyRefactoringUtil.isAppropriateContainerForIntroduceVariable(scope)) {
      throw new GrIntroduceRefactoringError(
        GroovyRefactoringBundle.message("refactoring.is.not.supported.in.the.current.context", REFACTORING_NAME));
    }
    return scope;
  }

  protected void checkExpression(GrExpression selectedExpr) {
    // Cannot perform refactoring in parameter default values

    PsiElement parent = selectedExpr.getParent();
    while (!(parent == null || parent instanceof GroovyFileBase || parent instanceof GrParameter)) {
      parent = parent.getParent();
    }

    if (checkInFieldInitializer(selectedExpr)) {
      throw new GrIntroduceRefactoringError(GroovyRefactoringBundle.message("refactoring.is.not.supported.in.the.current.context"));
    }

    if (parent instanceof GrParameter) {
      throw new GrIntroduceRefactoringError(GroovyRefactoringBundle.message("refactoring.is.not.supported.in.method.parameters"));
    }
  }

  @Override
  protected void checkVariable(GrVariable variable) throws GrIntroduceRefactoringError {
    throw new GrIntroduceRefactoringError(null);
  }

  @Override
  protected void checkOccurrences(PsiElement[] occurrences) {
    //nothing to do
  }

  private static boolean checkInFieldInitializer(GrExpression expr) {
    PsiElement parent = expr.getParent();
    if (parent instanceof GrClosableBlock) {
      return false;
    }
    if (parent instanceof GrField && expr == ((GrField)parent).getInitializerGroovy()) {
      return true;
    }
    if (parent instanceof GrExpression) {
      return checkInFieldInitializer(((GrExpression)parent));
    }
    return false;
  }

  /**
   * Inserts new variable declaratrions and replaces occurrences
   */
  public GrVariable runRefactoring(final GrIntroduceContext context, final GroovyIntroduceVariableSettings settings) {
    // Generating varibable declaration

    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(context.project);
    final GrVariableDeclaration varDecl = factory
      .createVariableDeclaration(settings.isDeclareFinal() ? new String[]{PsiModifier.FINAL} : null,
                                 (GrExpression)PsiUtil.skipParentheses(context.expression, false), settings.getSelectedType(),
                                 settings.getName());

    // Marker for caret posiotion
    try {
      /* insert new variable */
      GroovyRefactoringUtil.sortOccurrences(context.occurrences);
      if (context.occurrences.length == 0 || !(context.occurrences[0] instanceof GrExpression)) {
        throw new IncorrectOperationException("Wrong expression occurrence");
      }
      GrExpression firstOccurrence;
      if (settings.replaceAllOccurrences()) {
        firstOccurrence = ((GrExpression)context.occurrences[0]);
      }
      else {
        firstOccurrence = context.expression;
      }

      assert varDecl.getVariables().length > 0;

      resolveLocalConflicts(context.scope, varDecl.getVariables()[0].getName());
      // Replace at the place of first occurrence

      GrVariable insertedVar = replaceOnlyExpression(firstOccurrence, context, varDecl);
      boolean alreadyDefined = insertedVar != null;
      if (insertedVar == null) {
        // Insert before first occurrence
        insertedVar = insertVariableDefinition(context, settings, varDecl);
      }

      insertedVar.setType(settings.getSelectedType());

      //Replace other occurrences
      GrReferenceExpression refExpr = factory.createReferenceExpressionFromText(settings.getName());
      if (settings.replaceAllOccurrences()) {
        ArrayList<PsiElement> replaced = new ArrayList<PsiElement>();
        for (PsiElement occurrence : context.occurrences) {
          if (!(alreadyDefined && firstOccurrence.equals(occurrence))) {
            if (occurrence instanceof GrExpression) {
              GrExpression element = (GrExpression)occurrence;
              replaced.add(element.replaceWithExpression(refExpr, true));
              // For caret position
              if (occurrence.equals(context.expression)) {
                refreshPositionMarker(replaced.get(replaced.size() - 1));
              }
              refExpr = factory.createReferenceExpressionFromText(settings.getName());
            }
            else {
              throw new IncorrectOperationException("Expression occurrence to be replaced is not instance of GroovyPsiElement");
            }
          }
        }
        if (context.editor != null) {
          // todo implement it...
//              final PsiElement[] replacedOccurrences = replaced.toArray(new PsiElement[replaced.size()]);
//              highlightReplacedOccurrences(myProject, editor, replacedOccurrences);
        }
      }
      else {
        if (!alreadyDefined) {
          refreshPositionMarker(context.expression.replaceWithExpression(refExpr, true));
        }
      }
      // Setting caret to logical position
      if (context.editor != null && getPositionMarker() != null) {
        context.editor.getCaretModel().moveToOffset(getPositionMarker().getTextRange().getEndOffset());
        context.editor.getSelectionModel().removeSelection();
      }
      return insertedVar;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return null;
  }

  private static void resolveLocalConflicts(PsiElement tempContainer, String varName) {
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

  @Nullable
  private static String getFieldName(PsiElement element) {
    if (element instanceof GrAccessorMethod) element = ((GrAccessorMethod)element).getProperty();
    return element instanceof GrField ? ((GrField)element).getName() : null;
  }

  private void refreshPositionMarker(PsiElement position) {
    if (positionElement == null && position != null) {
      positionElement = position;
    }
  }

  private PsiElement getPositionMarker() {
    return positionElement;
  }

  private GrVariable insertVariableDefinition(GrIntroduceContext context,
                                              GroovyIntroduceVariableSettings settings,
                                              GrVariableDeclaration varDecl) throws IncorrectOperationException {
    LOG.assertTrue(context.occurrences.length > 0);

    GrStatement anchorElement = (GrStatement)findAnchor(context, settings, context.occurrences, context.scope);
    LOG.assertTrue(anchorElement != null);
    PsiElement realContainer = anchorElement.getParent();

    assert GroovyRefactoringUtil.isAppropriateContainerForIntroduceVariable(realContainer);

    if (!(realContainer instanceof GrLoopStatement)) {
      if (realContainer instanceof GrStatementOwner) {
        GrStatementOwner block = (GrStatementOwner)realContainer;
        varDecl = (GrVariableDeclaration)block.addStatementBefore(varDecl, anchorElement);
      }
    }
    else {
      // To replace branch body correctly
      String refId = varDecl.getVariables()[0].getName();
      GrBlockStatement newBody;
      final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(context.project);
      if (anchorElement.equals(context.expression)) {
        newBody = factory.createBlockStatement(varDecl);
      }
      else {
        replaceExpressionOccurrencesInStatement(anchorElement, context.expression, refId, settings.replaceAllOccurrences());
        newBody = factory.createBlockStatement(varDecl, anchorElement);
      }

      varDecl = (GrVariableDeclaration)newBody.getBlock().getStatements()[0];

      GrCodeBlock tempBlock = ((GrBlockStatement)((GrLoopStatement)realContainer).replaceBody(newBody)).getBlock();
      refreshPositionMarker(tempBlock.getStatements()[tempBlock.getStatements().length - 1]);
    }

    return varDecl.getVariables()[0];
  }

  private static void replaceExpressionOccurrencesInStatement(GrStatement stmt,
                                                              GrExpression expr,
                                                              String refText,
                                                              boolean replaceAllOccurrences)
    throws IncorrectOperationException {
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(stmt.getProject());
    GrReferenceExpression refExpr = factory.createReferenceExpressionFromText(refText);
    if (!replaceAllOccurrences) {
      expr.replaceWithExpression(refExpr, true);
    }
    else {
      PsiElement[] occurrences = GroovyRefactoringUtil.getExpressionOccurrences(expr, stmt);
      for (PsiElement occurrence : occurrences) {
        if (occurrence instanceof GrExpression) {
          GrExpression grExpression = (GrExpression)occurrence;
          grExpression.replaceWithExpression(refExpr, true);
          refExpr = factory.createReferenceExpressionFromText(refText);
        }
        else {
          throw new IncorrectOperationException();
        }
      }
    }
  }

  /**
   * Replaces an expression occurrence by appropriate variable declaration
   */
  @Nullable
  private GrVariable replaceOnlyExpression(@NotNull GrExpression expr,
                                           GrIntroduceContext context,
                                           @NotNull GrVariableDeclaration definition) throws IncorrectOperationException {

    if (context.scope.equals(expr.getParent()) &&
        !(context.scope instanceof GrLoopStatement) &&
        !(context.scope instanceof GrClosableBlock)) {
      definition = expr.replaceWithStatement(definition);
      if (expr.equals(context.expression)) {
        refreshPositionMarker(definition);
      }

      return definition.getVariables()[0];
    }
    return null;
  }
}
