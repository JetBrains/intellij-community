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
package org.jetbrains.plugins.groovy.refactoring.extract.closure;

import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.IntroduceTargetChooser;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.Function;
import com.intellij.util.PairFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParametersOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrMemberOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.HelpID;
import org.jetbrains.plugins.groovy.refactoring.extract.ExtractException;
import org.jetbrains.plugins.groovy.refactoring.extract.ExtractHandlerBase;
import org.jetbrains.plugins.groovy.refactoring.extract.InitialInfo;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;
import org.jetbrains.plugins.groovy.refactoring.ui.MethodOrClosureScopeChooser;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class ExtractClosureHandler extends ExtractHandlerBase implements RefactoringActionHandler, MethodOrClosureScopeChooser.JBPopupOwner {
  public static final String EXTRACT_CLOSURE = "Extract Closure";
  private JBPopup myPopup;

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file, DataContext dataContext) {
    final SelectionModel selectionModel = editor.getSelectionModel();
    if (!selectionModel.hasSelection()) {
      final int offset = editor.getCaretModel().getOffset();

      final List<GrExpression> expressions = GrIntroduceHandlerBase.collectExpressions(file, editor, offset);
      if (expressions.size() == 1) {
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
        IntroduceTargetChooser.showChooser(editor, expressions, callback, renderer);
        return;
      }
    }

    invoke(project, editor, file, selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
  }

  void invoke(Project project, Editor editor, PsiFile file, int start, int end) {
    try {
      invokeOnEditor(project, editor, file, start, end);
    }
    catch (ExtractException e) {
      CommonRefactoringUtil.showErrorHint(project, editor, e.getMessage(), EXTRACT_CLOSURE, HelpID.GROOVY_EXTRACT_CLOSURE);
    }
  }

  @Nullable
  protected ExtractClosureHelper getSettings(@NotNull InitialInfo initialInfo, GrParametersOwner owner, PsiElement toSearchFor) {
    final ExtractClosureDialog dialog = new ExtractClosureDialog(initialInfo, owner, toSearchFor);
    dialog.show();
    if (!dialog.isOK()) return null;

    return dialog.getHelper();
  }


  private void findScope(@NotNull PsiElement place,
                         @NotNull final Editor editor,
                         @NotNull PairFunction<GrParametersOwner, PsiElement, Object> callback) {
    final List<GrParametersOwner> scopes = new ArrayList<GrParametersOwner>();
    while (true) {
      final GrParametersOwner parent = PsiTreeUtil.getParentOfType(place, GrMethod.class/*, GrClosableBlock.class*/); //todo implement extract closure from closure
      if (parent == null) break;
      scopes.add(parent);
      place = parent;
    }

    if (scopes.size() == 0) {
      throw new ExtractException(GroovyRefactoringBundle.message("there.is.no.method.or.closure"));
    }
    else if (scopes.size() == 1) {
      final GrParametersOwner owner = scopes.get(0);
      if (owner instanceof GrMethod) {
        PsiMethod newMethod = SuperMethodWarningUtil.checkSuperMethod((PsiMethod)owner, RefactoringBundle.message("to.refactor"));
        if (newMethod == null) return;
        callback.fun(owner, newMethod);
      }
      else {
        callback.fun(owner, MethodOrClosureScopeChooser.findVariableToUse(owner));
      }
    }
    else {
      myPopup = MethodOrClosureScopeChooser.create(scopes, editor, this, callback);
      myPopup.showInBestPositionFor(editor);
    }
  }


  @Override
  public void performRefactoring(@NotNull final InitialInfo info,
                                 @NotNull GrMemberOwner owner,
                                 final GrStatementOwner declarationOwner,
                                 final Editor editor,
                                 PsiElement startElement) {
    findScope(startElement, editor, new PairFunction<GrParametersOwner, PsiElement, Object>() {
      @Override
      public Object fun(GrParametersOwner owner, PsiElement toSearchFor) {
        final ExtractClosureHelper helper = getSettings(info, owner, toSearchFor);
        if (helper == null) return null;

        if (helper.getOwner() instanceof GrMethod) {
          new ExtractClosureFromMethodProcessor(helper, editor, declarationOwner).run();
        }
        return null;
      }
    });
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    //do nothing
  }

  @Override
  public JBPopup get() {
    return myPopup;
  }
}
