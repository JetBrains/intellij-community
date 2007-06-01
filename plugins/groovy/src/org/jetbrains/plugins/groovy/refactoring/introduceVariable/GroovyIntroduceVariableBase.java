/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.refactoring.introduceVariable;

import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduceVariable.InputValidator;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLoopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.Arrays;
import java.util.Comparator;
import java.util.ArrayList;

/**
 * @author ilyas
 */
public abstract class GroovyIntroduceVariableBase implements RefactoringActionHandler {

  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.refactoring.introduceVariable.groovyIntroduceVariableBase");
  protected static String REFACTORING_NAME = GroovyRefactoringBundle.message("introduce.variable.title");

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, @Nullable DataContext dataContext) {
    if (!editor.getSelectionModel().hasSelection()) {
      editor.getSelectionModel().selectLineAtCaret();
    }
    trimSpaces(editor, file);
    if (invoke(project, editor, file, editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd())) {
      editor.getSelectionModel().removeSelection();
    }
  }

  private boolean invoke(final Project project, final Editor editor, PsiFile file, int startOffset, int endOffset) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    if (!(file instanceof GroovyFile)) {
      return false;
    }
    // Expression or block to be introduced as a variable
    GrExpression tempExpr = GroovyRefactoringUtil.findElementInRange(((GroovyFile) file), startOffset, endOffset, GrExpression.class);
    return invokeImpl(project, tempExpr, editor);
  }

  private boolean invokeImpl(final Project project, final GrExpression selectedExpr, final Editor editor) {

    if (selectedExpr == null) {
      String message = RefactoringBundle.getCannotRefactorMessage(GroovyRefactoringBundle.message("selected.block.should.represent.an.expression"));
      showErrorMessage(message, project);
      return false;
    }

    final PsiFile file = selectedExpr.getContainingFile();
    LOG.assertTrue(file != null, "expr.getContainingFile() == null");
    final GroovyElementFactory factory = GroovyElementFactory.getInstance(project);


    if (selectedExpr.getType() == PsiType.VOID) {
      String message = RefactoringBundle.getCannotRefactorMessage(GroovyRefactoringBundle.message("selected.expression.has.void.type"));
      showErrorMessage(message, project);
      return false;
    }

    // Get container element
    final PsiElement eclosingContainer = GroovyRefactoringUtil.getEnclosingContainer(selectedExpr);
    // TODO implement loop and fork statements as containers
    if (eclosingContainer == null || !(eclosingContainer instanceof GroovyPsiElement)) {
      return tempContainerNotFound(project);
    }
    final GroovyPsiElement tempContainer = ((GroovyPsiElement) eclosingContainer);
    if (!isAppropriateContainer(tempContainer)) {
      String message = RefactoringBundle.getCannotRefactorMessage(GroovyRefactoringBundle.message("refactoring.is.not.supported.in.the.current.context", REFACTORING_NAME));
      showErrorMessage(message, project);
      return false;
    }
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) return false;

    // Find occurences
    final PsiElement[] occurences = GroovyRefactoringUtil.getExpressionOccurences(GroovyRefactoringUtil.getUnparenthesizedExpr(selectedExpr), tempContainer);
    // Getting settings
    GroovyIntroduceVariableSettings settings = getSettings(project, editor, selectedExpr, selectedExpr.getType(), occurences, false, null);

    if (!settings.isOK()) {
      return false;
    }

    final String varName = settings.getEnteredName();
    PsiType varType = settings.getSelectedType();
    final boolean isFinal = settings.isDeclareFinal();
    final boolean replaceAllOccurences = settings.isReplaceAllOccurrences();

    // Generating varibable declaration
    final GrVariableDeclaration varDecl = factory.createVariableDeclaration(varName,
        GroovyRefactoringUtil.getUnparenthesizedExpr(selectedExpr), varType, isFinal);

    final Runnable runnable = new Runnable() {
      public void run() {
        try {
          /* insert new variable */
          sortOccurences(occurences);
          if (occurences.length == 0 || !(occurences[0] instanceof GrExpression)) {
            throw new IncorrectOperationException("Wrong expression occurence");
          }
          GrExpression firstOccurence;
          if (replaceAllOccurences) {
            firstOccurence = ((GrExpression) occurences[0]);
          } else {
            firstOccurence = selectedExpr;
          }
          // Replace at the place of first occurence
          boolean alreadyDefined = replaceAloneExpression(firstOccurence, tempContainer, varDecl);
          if (!alreadyDefined) {
            // Insert before first occurence
            insertVariableDefinition(tempContainer, selectedExpr, occurences, replaceAllOccurences, varDecl, factory);
          }

          //Replace other occurences
          GrReferenceExpression refExpr = factory.createReferenceExpressionFromText(varName);
          if (replaceAllOccurences) {
            ArrayList<PsiElement> replaced = new ArrayList<PsiElement>();
            for (PsiElement occurence : occurences) {
              if (!(alreadyDefined && firstOccurence.equals(occurence))) {
                if (occurence instanceof GrExpression) {
                  GrExpression element = (GrExpression) occurence;
                  // todo replace different case of replaceWithExpresssion method
                  replaced.add(element.replaceWithExpresssion(refExpr));
                  refExpr = factory.createReferenceExpressionFromText(varName);
                } else {
                  throw new IncorrectOperationException("Expression occurence to be replaced is not instance of GroovyPsiElement");
                }
              }
            }
            if (editor != null) {
              final PsiElement[] replacedOccurences = replaced.toArray(new PsiElement[replaced.size()]);
              highlightReplacedOccurences(project, editor, replacedOccurences);
            }
          } else {
            if (!alreadyDefined) {
              selectedExpr.replaceWithExpresssion(refExpr);
            }
          }
        } catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    };

    CommandProcessor.getInstance().executeCommand(
        project,
        new Runnable() {
          public void run() {
            ApplicationManager.getApplication().runWriteAction(runnable);
          }
        }, REFACTORING_NAME, null);
    return true;

  }

  /**
   * Calculates position to which new variable definition will be inserted.
   *
   * @param container
   * @param occurences
   * @param replaceAllOccurences
   * @param expr                 expression to be introduced as a variable
   * @return PsiElement, before what new definition will be inserted
   */
  @Nullable
  private PsiElement calculatePositionToInsertBefore(@NotNull PsiElement container,
                                                     PsiElement expr,
                                                     PsiElement[] occurences,
                                                     boolean replaceAllOccurences) {
    if (occurences.length == 0) return null;
    PsiElement candidate;
    if (occurences.length == 1 || !replaceAllOccurences) {
      candidate = expr;
    } else {
      sortOccurences(occurences);
      candidate = occurences[0];
    }
    while (candidate != null && !container.equals(candidate.getParent())) {
      candidate = candidate.getParent();
    }
    return candidate;
  }

  /**
   * Inserts new variable definiton according the contex
   */
  private void insertVariableDefinition(GroovyPsiElement tempContainer, GrExpression selectedExpr,
                                        PsiElement[] occurences, boolean replaceAllOccurences,
                                        GrVariableDeclaration varDecl, GroovyElementFactory factory) throws IncorrectOperationException {
    PsiElement anchorElement = calculatePositionToInsertBefore(tempContainer, selectedExpr, occurences, replaceAllOccurences);
    assert anchorElement instanceof GrStatement;
    if (!GroovyRefactoringUtil.isLoopOrForkStatement(tempContainer)) {
      tempContainer.addBefore(varDecl, anchorElement);
      if (tempContainer instanceof GrCodeBlock &&
          !((GrCodeBlock) tempContainer).mayUseNewLinesAsSeparators()) {
        tempContainer.addBefore(factory.createSemicolon(), anchorElement);
      } else {
        tempContainer.addBefore(factory.createNewLine(), anchorElement);
      }
    } else {
      GrOpenBlock newBody = factory.createOpenBlockFromStatements(varDecl, ((GrStatement) anchorElement));
      if (tempContainer instanceof GrLoopStatement) {
        ((GrLoopStatement) tempContainer).replaceBody(newBody);
      } else if (tempContainer instanceof GrIfStatement) {
        GrIfStatement ifStatement = ((GrIfStatement) tempContainer);
        if (anchorElement.equals(ifStatement.getThenBranch())) {
          ifStatement.replaceThenBranch(newBody);
          return;
        }
        if (anchorElement.equals(ifStatement.getElseBranch())) {
          ifStatement.replaceElseBranch(newBody);
          return;
        }
      }
    }
  }

  private void sortOccurences(PsiElement[] occurences) {
    Arrays.sort(occurences, new Comparator<PsiElement>() {
      public int compare(PsiElement elem1, PsiElement elem2) {
        if (elem1.getTextOffset() < elem2.getTextOffset()) {
          return -1;
        } else if (elem1.getTextOffset() > elem2.getTextOffset()) {
          return 1;
        } else {
          return 0;
        }
      }
    });
  }

  private boolean replaceAloneExpression(@NotNull GrExpression expr,
                                         @NotNull PsiElement context,
                                         @NotNull GrVariableDeclaration definition) throws IncorrectOperationException {
    if (context.equals(expr.getParent()) &&
        !GroovyRefactoringUtil.isResultExpression(expr) &&
        !GroovyRefactoringUtil.isLoopOrForkStatement(context)) {
      expr.replaceWithStatement(definition);
      return true;
    }
    return false;
  }

  private boolean tempContainerNotFound(final Project project) {
    String message = GroovyRefactoringBundle.message("refactoring.is.not.supported.in.the.current.context", REFACTORING_NAME);
    showErrorMessage(message, project);
    return false;
  }

  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, @Nullable DataContext dataContext) {
    // Does nothing
  }

  protected abstract GroovyIntroduceVariableSettings getSettings(final Project project, Editor editor, PsiElement expr,
                                                                 PsiType type, PsiElement[] occurrences, boolean decalreFinal,
                                                                 InputValidator validator);

  protected abstract void showErrorMessage(String message, Project project);

  protected abstract void highlightReplacedOccurences(final Project project, Editor editor, final PsiElement[] replacedOccurences);

  private static boolean isAppropriateContainer(PsiElement tempContainer) {
    return tempContainer instanceof GrCodeBlock ||
        tempContainer instanceof GroovyFile ||
        tempContainer instanceof GrCaseBlock ||
        GroovyRefactoringUtil.isLoopOrForkStatement(tempContainer);
  }

  private static void trimSpaces(Editor editor, PsiFile file) {
    int start = editor.getSelectionModel().getSelectionStart();
    int end = editor.getSelectionModel().getSelectionEnd();
    while (file.findElementAt(start) instanceof PsiWhiteSpace ||
        file.findElementAt(start) instanceof PsiComment ||
        (file.findElementAt(start) != null &&
            GroovyTokenTypes.mNLS.equals(file.findElementAt(start).getNode().getElementType()))) {
      start++;
    }
    while (file.findElementAt(end - 1) instanceof PsiWhiteSpace ||
        file.findElementAt(end - 1) instanceof PsiComment ||
        (file.findElementAt(end - 1) != null &&
            (GroovyTokenTypes.mNLS.equals(file.findElementAt(end - 1).getNode().getElementType()) ||
                GroovyTokenTypes.mSEMI.equals(file.findElementAt(end - 1).getNode().getElementType())))) {
      end--;
    }

    editor.getSelectionModel().setSelection(start, end);

  }
}
