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
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.GrReferenceAdjuster;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrMemberOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GrRefactoringError;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.refactoring.extract.ExtractUtil;
import org.jetbrains.plugins.groovy.refactoring.extract.GroovyExtractChooser;
import org.jetbrains.plugins.groovy.refactoring.extract.InitialInfo;
import org.jetbrains.plugins.groovy.refactoring.extract.ParameterInfo;

import java.util.ArrayList;

/**
 * @author ilyas
 */
public class GroovyExtractMethodHandler implements RefactoringActionHandler {
  protected static String REFACTORING_NAME = GroovyRefactoringBundle.message("extract.method.title");
  private static final Logger LOG = Logger.getInstance(GroovyExtractMethodHandler.class);

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, @Nullable DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    // select editor text fragment
    final SelectionModel model = editor.getSelectionModel();
    if (!model.hasSelection()) {
      model.selectLineAtCaret();
    }

    try {
      final InitialInfo initialInfo =
        GroovyExtractChooser.invoke(project, editor, file, model.getSelectionStart(), model.getSelectionEnd(), true);
      performRefactoring(initialInfo, editor);
    }
    catch (GrRefactoringError e) {
      CommonRefactoringUtil.showErrorHint(project, editor, e.getMessage(), REFACTORING_NAME, HelpID.EXTRACT_METHOD);
    }
  }

  private static void performRefactoring(@NotNull final InitialInfo initialInfo, final Editor editor) {
    final GrMemberOwner owner = PsiUtil.getMemberOwner(initialInfo.getStatements()[0]);
    LOG.assertTrue(owner!=null);
    final ExtractMethodInfoHelper helper = getSettings(initialInfo, owner);
    if (helper == null) return;

    CommandProcessor.getInstance().executeCommand(helper.getProject(), new Runnable() {
      public void run() {
        final AccessToken lock = ApplicationManager.getApplication().acquireWriteActionLock(GroovyExtractMethodHandler.class);
        try {
          createMethod(helper, owner);
          GrStatementOwner declarationOwner = GroovyRefactoringUtil.getDeclarationOwner(helper.getStatements()[0]);
          GrStatement realStatement = ExtractUtil.replaceStatement(declarationOwner, helper);

          // move to offset
          if (editor != null) {
            PsiDocumentManager.getInstance(helper.getProject()).commitDocument(editor.getDocument());
            editor.getSelectionModel().removeSelection();
            editor.getCaretModel().moveToOffset(ExtractUtil.getCaretOffset(realStatement));
          }
        }
        finally {
          lock.finish();
        }
      }
    }, REFACTORING_NAME, null);
  }

  private static void createMethod(ExtractMethodInfoHelper helper, GrMemberOwner owner) {
    final GrMethod method = ExtractUtil.createMethod(helper);
    PsiElement anchor = calculateAnchorToInsertBefore(owner, helper.getStatements()[0]);
    GrMethod newMethod = owner.addMemberDeclaration(method, anchor);
    renameParameterOccurrences(newMethod, helper);
    GrReferenceAdjuster.shortenReferences(newMethod);
    PsiElement prev = newMethod.getPrevSibling();
    IElementType elementType = prev.getNode().getElementType();
    if (!TokenSets.WHITE_SPACES_SET.contains(elementType) || !prev.getText().contains("\n")) {
      newMethod.getParent().getNode().addLeaf(GroovyTokenTypes.mNLS, "\n", newMethod.getNode());
    }
  }

  @Nullable
  private static ExtractMethodInfoHelper getSettings(@NotNull InitialInfo initialInfo, GrMemberOwner owner) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      final ExtractMethodInfoHelper helper = new ExtractMethodInfoHelper(initialInfo, "testMethod", owner);
      final PsiType type = helper.getOutputType();
      if (type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) || PsiType.VOID.equals(type)) {
        helper.setSpecifyType(false);
      }
      return helper;
    }

    GroovyExtractMethodDialog dialog = new GroovyExtractMethodDialog(initialInfo, owner);
    dialog.show();
    if (!dialog.isOK()) return null;

    return dialog.getHelper();
  }


  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    // does nothing
  }

  @Nullable
  private static PsiElement calculateAnchorToInsertBefore(GrMemberOwner owner, PsiElement startElement) {
    while (startElement != null && !isEnclosingDefinition(owner, startElement)) {
      if (startElement.getParent() instanceof GroovyFile) {
        return startElement.getNextSibling();
      }
      startElement = startElement.getParent();
      PsiElement parent = startElement.getParent();
      if (parent instanceof GroovyFile && ((GroovyFile) parent).getScriptClass() == owner) {
        return startElement.getNextSibling();
      }
    }
    return startElement == null ? null : startElement.getNextSibling();
  }

  private static boolean isEnclosingDefinition(GrMemberOwner owner, PsiElement startElement) {
    if (owner instanceof GrTypeDefinition) {
      GrTypeDefinition definition = (GrTypeDefinition) owner;
      return startElement.getParent() == definition.getBody();
    }
    return false;
  }

  private static void renameParameterOccurrences(GrMethod method, ExtractMethodInfoHelper helper) throws IncorrectOperationException {
    GrOpenBlock block = method.getBlock();
    if (block == null) return;
    GrStatement[] statements = block.getStatements();

    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(helper.getProject());
    for (ParameterInfo info : helper.getParameterInfos()) {
      final String oldName = info.getOldName();
      final String newName = info.getName();
      final ArrayList<GrExpression> result = new ArrayList<GrExpression>();
      if (!oldName.equals(newName)) {
        for (final GrStatement statement : statements) {
          statement.accept(new PsiRecursiveElementVisitor() {
            public void visitElement(final PsiElement element) {
              super.visitElement(element);
              if (element instanceof GrReferenceExpression) {
                GrReferenceExpression expr = (GrReferenceExpression) element;
                if (!expr.isQualified() && oldName.equals(expr.getName())) {
                  result.add(expr);
                }
              }
            }
          });
          for (GrExpression expr : result) {
            expr.replaceWithExpression(factory.createExpressionFromText(newName), false);
          }
        }
      }
    }
  }
}
