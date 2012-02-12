/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.psi.impl.DebugUtil;
import com.intellij.refactoring.HelpID;
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
import org.jetbrains.plugins.groovy.refactoring.GrRefactoringError;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;

import java.util.ArrayList;

/**
 * @author ilyas
 */
public class GrIntroduceVariableHandler extends GrIntroduceHandlerBase<GroovyIntroduceVariableSettings> {

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
      throw new GrRefactoringError(
        GroovyRefactoringBundle.message("refactoring.is.not.supported.in.the.current.context", REFACTORING_NAME));
    }
    if (!GroovyRefactoringUtil.isAppropriateContainerForIntroduceVariable(scope)) {
      throw new GrRefactoringError(
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
      throw new GrRefactoringError(GroovyRefactoringBundle.message("refactoring.is.not.supported.in.the.current.context"));
    }

    if (parent instanceof GrParameter) {
      throw new GrRefactoringError(GroovyRefactoringBundle.message("refactoring.is.not.supported.in.method.parameters"));
    }
  }

  @Override
  protected void checkVariable(GrVariable variable) throws GrRefactoringError {
    throw new GrRefactoringError(null);
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
   * Inserts new variable declarations and replaces occurrences
   */
  public GrVariable runRefactoring(final GrIntroduceContext context, final GroovyIntroduceVariableSettings settings) {
    // Generating variable declaration

    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(context.getProject());
    final GrVariableDeclaration varDecl = factory
      .createVariableDeclaration(settings.isDeclareFinal() ? new String[]{PsiModifier.FINAL} : null,
                                 (GrExpression)PsiUtil.skipParentheses(context.getExpression(), false), settings.getSelectedType(),
                                 settings.getName());

    // Marker for caret position
    try {
      /* insert new variable */
      GroovyRefactoringUtil.sortOccurrences(context.getOccurrences());
      if (context.getOccurrences().length == 0 || !(context.getOccurrences()[0] instanceof GrExpression)) {
        throw new IncorrectOperationException("Wrong expression occurrence");
      }
      GrExpression firstOccurrence;
      if (settings.replaceAllOccurrences()) {
        firstOccurrence = ((GrExpression)context.getOccurrences()[0]);
      }
      else {
        firstOccurrence = context.getExpression();
      }

      assert varDecl.getVariables().length > 0;

      resolveLocalConflicts(context.getScope(), varDecl.getVariables()[0].getName());
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
        for (PsiElement occurrence : context.getOccurrences()) {
          if (!(alreadyDefined && firstOccurrence.equals(occurrence))) {
            if (occurrence instanceof GrExpression) {
              GrExpression element = (GrExpression)occurrence;
              replaced.add(element.replaceWithExpression(refExpr, true));
              // For caret position
              if (occurrence.equals(context.getExpression())) {
                refreshPositionMarker(replaced.get(replaced.size() - 1));
              }
              refExpr = factory.createReferenceExpressionFromText(settings.getName());
            }
            else {
              throw new IncorrectOperationException("Expression occurrence to be replaced is not instance of GroovyPsiElement");
            }
          }
        }
        if (context.getEditor() != null) {
          // todo implement it...
//              final PsiElement[] replacedOccurrences = replaced.toArray(new PsiElement[replaced.size()]);
//              highlightReplacedOccurrences(myProject, editor, replacedOccurrences);
        }
      }
      else {
        if (!alreadyDefined) {
          refreshPositionMarker(context.getExpression().replaceWithExpression(refExpr, true));
        }
      }
      // Setting caret to logical position
      if (context.getEditor() != null && getPositionMarker() != null) {
        context.getEditor().getCaretModel().moveToOffset(getPositionMarker().getTextRange().getEndOffset());
        context.getEditor().getSelectionModel().removeSelection();
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
    LOG.assertTrue(context.getOccurrences().length > 0);

    PsiElement anchor = findAnchor(context, settings, context.getOccurrences(), context.getScope());
    if (!(anchor instanceof GrStatement)) {
      StringBuilder error = new StringBuilder("scope:");
      error.append(DebugUtil.psiToString(context.getScope(), true, false));

      error.append("occurrences: ");
      for (PsiElement occurrence : context.getOccurrences()) {
        error.append(DebugUtil.psiToString(occurrence, true, false));
      }

      LOG.error(error.toString());
    }
    GrStatement anchorElement = (GrStatement)anchor;
    PsiElement realContainer = anchorElement.getParent();

    LOG.assertTrue(GroovyRefactoringUtil.isAppropriateContainerForIntroduceVariable(realContainer));

    if (realContainer instanceof GrLoopStatement || realContainer instanceof GrIfStatement) {
      boolean isThenBranch = realContainer instanceof GrIfStatement && anchorElement.equals(((GrIfStatement)realContainer).getThenBranch());

      // To replace branch body correctly
      String refId = varDecl.getVariables()[0].getName();

      GrBlockStatement newBody;
      final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(context.getProject());
      if (context.getExpression().equals(PsiUtil.skipParentheses(anchorElement, false))) {
        newBody = factory.createBlockStatement(varDecl);
      }
      else {
        replaceExpressionOccurrencesInStatement(anchorElement, context.getExpression(), refId, settings.replaceAllOccurrences());
        newBody = factory.createBlockStatement(varDecl, anchorElement);
      }

      varDecl = (GrVariableDeclaration)newBody.getBlock().getStatements()[0];

      GrCodeBlock tempBlock;
      if (realContainer instanceof GrIfStatement) {
        if (isThenBranch) {
          tempBlock = ((GrIfStatement)realContainer).replaceThenBranch(newBody).getBlock();
        }
        else {
          tempBlock = ((GrIfStatement)realContainer).replaceElseBranch(newBody).getBlock();
        }
      }
      else {
        tempBlock = ((GrLoopStatement)realContainer).replaceBody(newBody).getBlock();
      }
      refreshPositionMarker(tempBlock.getStatements()[tempBlock.getStatements().length - 1]);
    }
    else {
      if (realContainer instanceof GrStatementOwner) {
        GrStatementOwner block = (GrStatementOwner)realContainer;
        varDecl = (GrVariableDeclaration)block.addStatementBefore(varDecl, anchorElement);
      }
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
      expr.replaceWithExpression(refExpr, false);
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

    if (context.getScope().equals(expr.getParent()) &&
        !(context.getScope() instanceof GrLoopStatement) &&
        !(context.getScope() instanceof GrClosableBlock)) {
      definition = expr.replaceWithStatement(definition);
      if (expr.equals(context.getExpression())) {
        refreshPositionMarker(definition);
      }

      return definition.getVariables()[0];
    }
    return null;
  }

  @Override
  protected String getRefactoringName() {
    return REFACTORING_NAME;
  }

  @Override
  protected String getHelpID() {
    return HelpID.INTRODUCE_VARIABLE;
  }

  protected GroovyIntroduceVariableDialog getDialog(GrIntroduceContext context) {
    final GroovyVariableValidator validator = new GroovyVariableValidator(context);
    return new GroovyIntroduceVariableDialog(context, validator);
  }
}
