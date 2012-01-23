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
package org.jetbrains.plugins.groovy.refactoring.introduce.parameter;

import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.IntroduceTargetChooser;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.Function;
import com.intellij.util.PairFunction;
import com.intellij.util.Processor;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParametersOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceDialog;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceRefactoringError;
import org.jetbrains.plugins.groovy.refactoring.ui.MethodOrClosureScopeChooser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.jetbrains.plugins.groovy.refactoring.HelpID.GROOVY_INTRODUCE_PARAMETER;

/**
 * @author Maxim.Medvedev
 */
public class GrIntroduceParameterHandler implements RefactoringActionHandler, MethodOrClosureScopeChooser.JBPopupOwner {
  private static final Logger LOG = Logger.getInstance(GrIntroduceParameterHandler.class);

  private JBPopup myEnclosingMethodsPopup;

  public void invoke(final @NotNull Project project, final Editor editor, final PsiFile file, final @Nullable DataContext dataContext) {
    final SelectionModel selectionModel = editor.getSelectionModel();
    if (!selectionModel.hasSelection()) {
      final int offset = editor.getCaretModel().getOffset();

      final List<GrExpression> expressions = GrIntroduceHandlerBase.collectExpressions(file, editor, offset);
      if (expressions.isEmpty()) {
        final GrVariable variable = GrIntroduceHandlerBase.findVariableAtCaret(file, editor, offset);
        if (variable == null || variable instanceof GrField || variable instanceof GrParameter) {
          selectionModel.selectLineAtCaret();
        }
        else {
          final TextRange textRange = variable.getTextRange();
          selectionModel.setSelection(textRange.getStartOffset(), textRange.getEndOffset());
        }
      }
      else if (expressions.size() == 1) {
        final TextRange textRange = expressions.get(0).getTextRange();
        selectionModel.setSelection(textRange.getStartOffset(), textRange.getEndOffset());
      }
      else {
        final Pass<GrExpression> callback = new Pass<GrExpression>() {
          public void pass(final GrExpression selectedValue) {
            invoke(project, editor, file, selectedValue.getTextRange().getStartOffset(), selectedValue.getTextRange().getEndOffset());
          }
        };
        final Function<GrExpression, String> renderer = new Function<GrExpression, String>() {
          @Override
          public String fun(GrExpression grExpression) {
            return grExpression.getText();
          }
        };
        IntroduceTargetChooser.showChooser(editor, expressions, callback, renderer
        );
        return;
      }
    }
    invoke(project, editor, file, selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
  }

  private void invoke(final Project project, final Editor editor, PsiFile file, int startOffset, int endOffset) {
    try {
      PsiDocumentManager.getInstance(project).commitAllDocuments();
      if (!(file instanceof GroovyFileBase)) {
        throw new GrIntroduceRefactoringError(GroovyRefactoringBundle.message("only.in.groovy.files"));
      }
      if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) {
        throw new GrIntroduceRefactoringError(RefactoringBundle.message("readonly.occurences.found"));
      }

      GrExpression selectedExpr = GrIntroduceHandlerBase.findExpression(file, startOffset, endOffset);
      final GrVariable variable = GrIntroduceHandlerBase.findVariable(file, startOffset, endOffset);
      if (variable == null && selectedExpr == null) {
        throw new GrIntroduceRefactoringError(null);
      }

      findScope(selectedExpr, variable, editor, project);
    }
    catch (GrIntroduceRefactoringError e) {
      CommonRefactoringUtil.showErrorHint(project, editor, RefactoringBundle.getCannotRefactorMessage(e.getMessage()), RefactoringBundle.message("introduce.parameter.title"),
                                          GROOVY_INTRODUCE_PARAMETER);
    }
  }

  private void findScope(@Nullable final GrExpression expression, @Nullable final GrVariable variable, @NotNull final Editor editor, @NotNull final Project project) {
    LOG.assertTrue(expression != null || variable != null);
    
    PsiElement place = expression == null ? variable : expression;

    final List<GrParametersOwner> scopes = new ArrayList<GrParametersOwner>();
    while (true) {
      final GrParametersOwner parent = PsiTreeUtil.getParentOfType(place, GrMethod.class, GrClosableBlock.class);
      if (parent == null) break;
      scopes.add(parent);
      place = parent;
    }

    if (scopes.size() == 0) {
      throw new GrIntroduceRefactoringError(GroovyRefactoringBundle.message("there.is.no.method.or.closure"));
    }
    else if (scopes.size() == 1) {
      final GrParametersOwner owner = scopes.get(0);
      final PsiElement toSearchFor;
      if (owner instanceof GrMethod) {
        toSearchFor = SuperMethodWarningUtil.checkSuperMethod((PsiMethod)owner, RefactoringBundle.message("to.refactor"));
        if (toSearchFor == null) return; //if it is null, refactoring was canceled
      }
      else {
        toSearchFor = MethodOrClosureScopeChooser.findVariableToUse(owner);
      }
      getContext(project, editor, expression, variable, owner, toSearchFor);
    }
    else {
      myEnclosingMethodsPopup = MethodOrClosureScopeChooser.create(scopes, editor, this, new PairFunction<GrParametersOwner, PsiElement, Object>() {
        @Override
        public Object fun(GrParametersOwner owner, PsiElement element) {
          getContext(project, editor, expression, variable, owner, element);
          return null;
        }
      });
      myEnclosingMethodsPopup.showInBestPositionFor(editor);
    }
  }

  @Override
  public JBPopup get() {
    return myEnclosingMethodsPopup;
  }

  protected void getContext(@NotNull Project project,
                            @NotNull Editor editor,
                            @Nullable GrExpression expression,
                            @Nullable GrVariable variable,
                            @NotNull GrParametersOwner toReplaceIn,
                            @Nullable PsiElement toSearchFor) {
    LOG.assertTrue(expression != null || variable != null);
    
    GrIntroduceContext context;
    if (variable == null) {
      final PsiElement[] occurrences = findOccurrences(expression, toReplaceIn);
      context = new GrIntroduceContext(project, editor, expression, occurrences, toReplaceIn, variable);
    }
    else {
      final List<PsiElement> list = Collections.synchronizedList(new ArrayList<PsiElement>());
      ReferencesSearch.search(variable, new LocalSearchScope(toReplaceIn)).forEach(new Processor<PsiReference>() {
        @Override
        public boolean process(PsiReference psiReference) {
          final PsiElement element = psiReference.getElement();
          if (element != null) {
            list.add(element);
          }
          return true;
        }
      });
      context = new GrIntroduceContext(project, editor, variable.getInitializerGroovy(), list.toArray(new PsiElement[list.size()]), toReplaceIn, variable);
    }

    showDialog(new GrIntroduceParameterContext(context, toReplaceIn, toSearchFor));
  }


  protected void showDialog(GrIntroduceParameterContext context) {
    TObjectIntHashMap<GrParameter> toRemove = GroovyIntroduceParameterUtil.findParametersToRemove(context);
    final GrIntroduceDialog<GrIntroduceParameterSettings> dialog = new GrIntroduceParameterDialog(context, toRemove);
    dialog.show();
  }

  @NotNull
  private static PsiElement[] findOccurrences(@NotNull GrExpression expression, PsiElement scope) {
    final PsiElement expr = PsiUtil.skipParentheses(expression, false);
    if (expr == null) return PsiElement.EMPTY_ARRAY;

    final PsiElement[] occurrences = GroovyRefactoringUtil.getExpressionOccurrences(expr, scope, true);
    if (occurrences == null || occurrences.length == 0) {
      throw new GrIntroduceRefactoringError(GroovyRefactoringBundle.message("no.occurences.found"));
    }
    return occurrences;
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    // Does nothing
  }
}
