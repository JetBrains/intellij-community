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

package org.jetbrains.plugins.groovy.refactoring.inline;

import com.intellij.lang.refactoring.InlineHandler;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringMessageDialog;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.ReachingDefinitionsDfaInstance;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.ReachingDefinitionsSemilattice;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAEngine;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.ArrayList;
import java.util.Collection;

import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectHashMap;

/**
 * @author ilyas
 */
public class GroovyInlineVariableUtil {

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.refactoring.inline.GroovyInlineVariableUtil");
  public static final String REFACTORING_NAME = GroovyRefactoringBundle.message("inline.variable.title");


  /**
   * Creates new inliner for local variable occurences
   */
  static InlineHandler.Inliner createInlinerForLocalVariable(final GrVariable variable) {
    return new InlineHandler.Inliner() {

      @Nullable
      public Collection<String> getConflicts(PsiReference reference, PsiElement referenced) {
        ArrayList<String> conflicts = new ArrayList<String>();
        GrExpression expr = (GrExpression) reference.getElement();
        if (expr.getParent() instanceof GrAssignmentExpression) {
          GrAssignmentExpression parent = (GrAssignmentExpression) expr.getParent();
          if (expr.equals(parent.getLValue())) {
            conflicts.add(GroovyRefactoringBundle.message("local.varaible.is.lvalue"));
          }
        }
        return conflicts;
      }

      public void inlineReference(final PsiReference reference, final PsiElement referenced) {
        GrExpression exprToBeReplaced = (GrExpression) reference.getElement();
        assert variable.getInitializerGroovy() != null;
        GrExpression initializerGroovy = variable.getInitializerGroovy();
        assert initializerGroovy != null;
        GrExpression tempExpr = initializerGroovy;
        while (tempExpr instanceof GrParenthesizedExpression) {
          tempExpr = ((GrParenthesizedExpression) tempExpr).getOperand();
        }
        Project project = variable.getProject();
        GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);
        GrExpression newExpr = factory.createExpressionFromText(tempExpr.getText());

        try {
          newExpr = exprToBeReplaced.replaceWithExpression(newExpr, true);
          FileEditorManager manager = FileEditorManager.getInstance(project);
          Editor editor = manager.getSelectedTextEditor();
          GroovyRefactoringUtil.highlightOccurrences(project, editor, new PsiElement[]{newExpr});
          WindowManager.getInstance().getStatusBar(project).setInfo(GroovyRefactoringBundle.message("press.escape.to.remove.the.highlighting"));
        } catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    };
  }

  /**
   * Returns Settings object for referenced definition in case of local variable
   */
  static InlineHandler.Settings inlineLocalVariableSettings(final GrVariable variable, Editor editor, boolean invokedOnReference) {
    final String localName = variable.getName();
    final Project project = variable.getProject();
    final Collection<PsiReference> refs = ReferencesSearch.search(variable).findAll();
    ArrayList<PsiElement> exprs = new ArrayList<PsiElement>();
    for (PsiReference ref : refs) {
      exprs.add(ref.getElement());
    }
//    removeRedundantReferences(variable, editor, exprs, invokedOnReference);

    GroovyRefactoringUtil.highlightOccurrences(project, editor, exprs.toArray(PsiElement.EMPTY_ARRAY));
    if (variable.getInitializerGroovy() == null) {
      String message = GroovyRefactoringBundle.message("cannot.find.a.single.definition.to.inline.local.var");
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INLINE_VARIABLE, variable.getProject());
      return null;
    }
    if (refs.isEmpty()) {
      String message = GroovyRefactoringBundle.message("variable.is.never.used.0", variable.getName());
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INLINE_VARIABLE, variable.getProject());
      return null;
    }

    return inlineDialogResult(localName, project, refs);
  }

  /**
   * Shows dialog with question to inline
   */
  private static InlineHandler.Settings inlineDialogResult(String localName, Project project, Collection<PsiReference> refs) {
    Application application = ApplicationManager.getApplication();
    if (!application.isUnitTestMode()) {
      final String question = GroovyRefactoringBundle.message("inline.local.variable.prompt.0.1", localName, refs.size());
      RefactoringMessageDialog dialog = new RefactoringMessageDialog(
          REFACTORING_NAME,
          question,
          HelpID.INLINE_VARIABLE,
          "OptionPane.questionIcon",
          true,
          project);
      dialog.show();
      if (!dialog.isOK()) {
        WindowManager.getInstance().getStatusBar(project).setInfo(GroovyRefactoringBundle.message("press.escape.to.remove.the.highlighting"));
        return null;
      }
    }

    return new InlineHandler.Settings() {
      public boolean isOnlyOneReferenceToInline() {
        return false;
      }
    };
  }


  /*
  Remove overriden definitions from scope
   */
  private static void removeRedundantReferences(GrVariable variable, Editor editor, Collection<PsiElement> exprs, boolean invokedOnReference) {
    Document document = editor.getDocument();
    PsiFile file = PsiDocumentManager.getInstance(variable.getProject()).getPsiFile(document);
    if (file == null) return;
    if (invokedOnReference) {
      int offset = editor.getCaretModel().getOffset();
      PsiReference reference = file.findReferenceAt(offset);
      if (reference == null) reference = file.findReferenceAt(offset - 1);
      if (reference == null || !(reference instanceof GrReferenceExpression)) return;
      PsiElement definition = getReachingDefinition(variable, ((GrReferenceExpression) reference));
    }

  }

  private static PsiElement getReachingDefinition(GrVariable variable, GrReferenceExpression expr) {
    GrVariableDeclarationOwner owner = PsiTreeUtil.getParentOfType(variable, GrVariableDeclarationOwner.class);
    if (owner == null || !(owner instanceof GrControlFlowOwner)) return null;
    Instruction[] flow = ((GrControlFlowOwner) owner).getControlFlow();
    ReachingDefinitionsDfaInstance dfaInstance = new ReachingDefinitionsDfaInstance(flow);
    ReachingDefinitionsSemilattice lattice = new ReachingDefinitionsSemilattice();
    DFAEngine<TIntObjectHashMap<TIntHashSet>> engine = new DFAEngine<TIntObjectHashMap<TIntHashSet>>(flow, dfaInstance, lattice);
    ArrayList<TIntObjectHashMap<TIntHashSet>> dfaResult = engine.performDFA();


    // todo implement appropriate DFA
    return expr.resolve();
  }

}
