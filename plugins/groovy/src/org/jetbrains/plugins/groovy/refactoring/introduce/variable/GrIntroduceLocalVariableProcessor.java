/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.diagnostic.LogMessageEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
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
public class GrIntroduceLocalVariableProcessor {
  private static final Logger LOG = Logger.getInstance(GrIntroduceLocalVariableProcessor.class);

  private final GrIntroduceContext myContext;
  private final GroovyIntroduceVariableSettings mySettings;
  private final PsiElement[] myOccurrences;
  private GrExpression myExpression;
  private final GrIntroduceVariableHandler myHandler;

  public GrIntroduceLocalVariableProcessor(@NotNull GrIntroduceContext context,
                                           @NotNull GroovyIntroduceVariableSettings settings,
                                           @NotNull PsiElement[] occurrences,
                                           @NotNull GrExpression expression,
                                           @NotNull GrIntroduceVariableHandler handler) {

    myContext = context;
    mySettings = settings;
    myOccurrences = settings.replaceAllOccurrences() ? occurrences : new PsiElement[]{expression};
    myExpression = expression;
    myHandler = handler;
  }

  @NotNull
  public GrVariable processExpression(@NotNull GrVariableDeclaration declaration) {
    resolveLocalConflicts(myContext.getScope(), mySettings.getName());

    preprocessOccurrences();

    boolean isExpressionFirstOccurrence = myOccurrences[0] == myExpression;

    final PsiElement[] replaced = processOccurrences();

    GrStatement anchor = getAnchor(replaced);
    if (isControlStatementBranch(anchor)) {
      anchor = insertBraces(replaced, anchor);
    }

    GrVariable variable = insertVariableDefinition(declaration, anchor);

    if (isExpressionFirstOccurrence) {
      final PsiElement expressionSkipped = PsiUtil.skipParentheses(replaced[0], true);
      if (PsiUtil.isExpressionStatement(expressionSkipped) &&
          expressionSkipped.getParent() == variable.getParent().getParent()) {
        expressionSkipped.delete();
        refreshPositionMarker(variable);
      }
    }

    RefactoringUtil.highlightAllOccurrences(myContext.getProject(), replaced, myContext.getEditor());
    return variable;
  }

  private GrStatement insertBraces(PsiElement[] replaced, GrStatement anchor) {
    List<SmartPsiElementPointer<PsiElement>> replacedPointers = ContainerUtil.newArrayList();
    final SmartPointerManager pointerManager = SmartPointerManager.getInstance(myContext.getProject());
    for (PsiElement element : replaced) {
      replacedPointers.add(pointerManager.createSmartPsiElementPointer(element));
    }
    SmartPsiElementPointer<GrStatement> anchorPointer = pointerManager.createSmartPsiElementPointer(anchor);

    final Document document = myContext.getEditor().getDocument();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myContext.getProject());
    documentManager.doPostponedOperationsAndUnblockDocument(document);
    final TextRange range = anchor.getTextRange();
    document.insertString(range.getEndOffset(), "}");
    document.insertString(skipSpacesBackward(document, range.getStartOffset()), "{");
    documentManager.commitDocument(document);

    for (int i = 0; i < replacedPointers.size(); i++) {
      SmartPsiElementPointer<PsiElement> pointer = replacedPointers.get(i);
      replaced[i] = pointer.getElement();
    }

    anchor = anchorPointer.getElement();
    CodeStyleManager.getInstance(myContext.getProject()).reformat(anchor.getParent());
    return anchor;
  }

  private void refreshPositionMarker(PsiElement e) {
    myHandler.refreshPositionMarker(myContext.getEditor().getDocument().createRangeMarker(e.getTextRange()));
  }

  private static int skipSpacesBackward(Document document, int offset) {
    if (offset == 0) return offset;
    final CharSequence sequence = document.getCharsSequence();
    while (Character.isSpaceChar(sequence.charAt(offset - 1))) {
      offset--;
    }
    return offset;
  }

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

      boolean isOriginal = myExpression == occurrence;

      final GrExpression replaced = ((GrExpression)occurrence).replaceWithExpression(templateRef, true);
      result.add(replaced);

      if (isOriginal) {
        refreshPositionMarker(replaced);
      }
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
  private GrVariable insertVariableDefinition(@NotNull GrVariableDeclaration declaration, final GrStatement anchor) throws IncorrectOperationException {
    LOG.assertTrue(myOccurrences.length > 0);

    PsiElement realContainer = anchor.getParent();

    GrStatementOwner block = (GrStatementOwner)realContainer;
    declaration = (GrVariableDeclaration)block.addStatementBefore(declaration, anchor);

    final GrVariable variable = declaration.getVariables()[0];
    variable.setType(mySettings.getSelectedType());

    JavaCodeStyleManager.getInstance(declaration.getProject()).shortenClassReferences(declaration);
    return variable;
  }

  private GrStatement getAnchor(PsiElement[] replaced) {
    PsiElement anchor = GrIntroduceHandlerBase.findAnchor(replaced, myContext.getScope());
    if (!(anchor instanceof GrStatement)) {
      StringBuilder error = new StringBuilder("scope:");
      error.append(myContext.getScope());
      error.append("\n---------------------------------------\n\n");
      error.append("occurrences: ");
      for (PsiElement occurrence : myOccurrences) {
        error.append(occurrence);
        error.append("\n------------------\n");
      }

      LogMessageEx.error(LOG, "cannot find anchor for variable", error.toString());
    }
    return (GrStatement)anchor;
  }

  @Nullable
  private static String getFieldName(@Nullable PsiElement element) {
    if (element instanceof GrAccessorMethod) element = ((GrAccessorMethod)element).getProperty();
    return element instanceof GrField ? ((GrField)element).getName() : null;
  }

}
