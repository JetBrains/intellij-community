/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.refactoring.extract.method;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.GrReferenceAdjuster;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrMemberOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.extract.ExtractException;
import org.jetbrains.plugins.groovy.refactoring.extract.ExtractHandlerBase;
import org.jetbrains.plugins.groovy.refactoring.extract.ExtractUtil;
import org.jetbrains.plugins.groovy.refactoring.extract.InitialInfo;

/**
 * @author ilyas
 */
public class GroovyExtractMethodHandler extends ExtractHandlerBase<ExtractMethodInfoHelper> implements RefactoringActionHandler {

  private static final Logger LOG = Logger.getInstance(GroovyExtractMethodHandler.class);
  protected static String REFACTORING_NAME = GroovyRefactoringBundle.message("extract.method.title");
  private String myInvokeResult = "ok";

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    invoke(project, editor, file);
  }

  void invoke(Project project, Editor editor, PsiFile file) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    // select editor text fragment
    final SelectionModel model = editor.getSelectionModel();
    if (!model.hasSelection()) {
      model.selectLineAtCaret();
    }

    try {
      invokeOnEditor(project, editor, file, model.getSelectionStart(), model.getSelectionEnd());
    }
    catch (ExtractException e) {
      myInvokeResult = e.getMessage();
    }
  }

  public void performRefactoring(@NotNull final ExtractMethodInfoHelper helper,
                                 @NotNull final GrMemberOwner owner,
                                 final GrStatementOwner declarationOwner,
                                 final Editor editor,
                                 final PsiElement startElement) {

    final String methodName = helper.getName();
    final GrMethod method = ExtractUtil.createMethodByHelper(methodName, helper);
    final Runnable runnable = new Runnable() {
      public void run() {
        try {
          PsiElement anchor = ExtractUtil.calculateAnchorToInsertBefore(owner, startElement);
          GrMethod newMethod = owner.addMemberDeclaration(method, anchor);
          ExtractUtil.renameParameterOccurrences(newMethod, helper);
          GrReferenceAdjuster.shortenReferences(newMethod);
          GrStatement realStatement;

          if (declarationOwner != null && !ExtractUtil.isSingleExpression(helper.getStatements())) {
            // Replace set of statements
            final GrStatement[] newStatement = ExtractUtil.createResultStatement(helper, methodName);
            // add call statement
            final GrStatement[] statements = helper.getStatements();
            assert statements.length > 0;
            realStatement = null;
            for (GrStatement statement : newStatement) {
              realStatement = declarationOwner.addStatementBefore(statement, statements[0]);
            }
            assert realStatement != null;
            // remove old statements
            ExtractUtil.removeOldStatements(declarationOwner, helper);
            PsiImplUtil.removeNewLineAfter(realStatement);
          }
          else {
            // Expression call replace
            GrExpression methodCall = ExtractUtil.createMethodCallByHelper(methodName, helper);
            GrExpression oldExpr = (GrExpression)helper.getStatements()[0];
            realStatement = oldExpr.replaceWithExpression(methodCall, true);
          }
          GrReferenceAdjuster.shortenReferences(realStatement);

          PsiElement prev = newMethod.getPrevSibling();
          IElementType elementType = prev.getNode().getElementType();
          if (!TokenSets.WHITE_SPACES_SET.contains(elementType) || !prev.getText().contains("\n")) {
            newMethod.getParent().getNode().addLeaf(GroovyTokenTypes.mNLS, "\n", newMethod.getNode());
          }

          // move to offset
          if (editor != null) {
            PsiDocumentManager.getInstance(helper.getProject()).commitDocument(editor.getDocument());
            editor.getCaretModel().moveToOffset(ExtractUtil.getCaretOffset(realStatement));
          }
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    };

    Project project = helper.getProject();
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(runnable);
        editor.getSelectionModel().removeSelection();
      }
    }, REFACTORING_NAME, null);
  }

  @Override
  public ExtractMethodInfoHelper getSettings(@NotNull InitialInfo initialInfo) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      final ExtractMethodInfoHelper helper = new ExtractMethodInfoHelper(initialInfo, "testMethod");
      final PsiType type = helper.getOutputType();
      if (type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) || type.equalsToText("void")) {
        helper.setSpecifyType(false);
      }
      return helper;
    }

    GroovyExtractMethodDialog dialog = new GroovyExtractMethodDialog(initialInfo);
    dialog.show();
    if (!dialog.isOK()) return null;

    return dialog.getHelper();
  }


  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    // does nothing
  }

  public String getInvokeResult() {
    return myInvokeResult;
  }
}
