// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.introduce.parameter;

import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParameterListOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.refactoring.GrRefactoringError;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.HelpID;
import org.jetbrains.plugins.groovy.refactoring.extract.GroovyExtractChooser;
import org.jetbrains.plugins.groovy.refactoring.extract.InitialInfo;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;
import org.jetbrains.plugins.groovy.refactoring.introduce.IntroduceOccurrencesChooser;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;
import org.jetbrains.plugins.groovy.refactoring.introduce.variable.GrIntroduceVariableHandler;
import org.jetbrains.plugins.groovy.refactoring.ui.MethodOrClosureScopeChooser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Maxim.Medvedev
 */
public class GrIntroduceParameterHandler implements RefactoringActionHandler, MethodOrClosureScopeChooser.JBPopupOwner {
  private JBPopup myEnclosingMethodsPopup;

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file, @Nullable final DataContext dataContext) {
    if (editor == null || file == null) return;
    final SelectionModel selectionModel = editor.getSelectionModel();
    if (!selectionModel.hasSelection()) {
      final int offset = editor.getCaretModel().getOffset();

      final List<GrExpression> expressions = GrIntroduceHandlerBase.collectExpressions(file, editor, offset, false);
      if (expressions.isEmpty()) {
        GrIntroduceHandlerBase.updateSelectionForVariable(editor, file, selectionModel, offset);
      }
      else if (expressions.size() == 1 || ApplicationManager.getApplication().isUnitTestMode()) {
        final TextRange textRange = expressions.get(0).getTextRange();
        selectionModel.setSelection(textRange.getStartOffset(), textRange.getEndOffset());
      }
      else {
        IntroduceTargetChooser.showChooser(editor, expressions, new Pass<>() {
                                             @Override
                                             public void pass(final GrExpression selectedValue) {
                                               invoke(project, editor, file, selectedValue.getTextRange().getStartOffset(), selectedValue.getTextRange().getEndOffset());
                                             }
                                           }, grExpression -> grExpression.getText()
        );
        return;
      }
    }
    invoke(project, editor, file, selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
  }

  private void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file, int startOffset, int endOffset) {
    try {
      final InitialInfo initialInfo = GroovyExtractChooser.invoke(project, editor, file, startOffset, endOffset, false);
      chooseScopeAndRun(initialInfo, editor);
    }
    catch (GrRefactoringError e) {
      if (ApplicationManager.getApplication().isUnitTestMode()) throw e;
      CommonRefactoringUtil.showErrorHint(project, editor, e.getMessage(), RefactoringBundle.message("introduce.parameter.title"),
                                          HelpID.GROOVY_INTRODUCE_PARAMETER);
    }
  }

  private void chooseScopeAndRun(@NotNull final InitialInfo initialInfo, @NotNull final Editor editor) {
    final List<GrParameterListOwner> scopes = findScopes(initialInfo);

    if (scopes.isEmpty()) {
      throw new GrRefactoringError(GroovyRefactoringBundle.message("there.is.no.method.or.closure"));
    }
    else if (scopes.size() == 1 || ApplicationManager.getApplication().isUnitTestMode()) {
      final GrParameterListOwner owner = scopes.get(0);
      final PsiElement toSearchFor;
      if (owner instanceof GrMethod) {
        toSearchFor = SuperMethodWarningUtil.checkSuperMethod((PsiMethod)owner);
        if (toSearchFor == null) return; //if it is null, refactoring was canceled
      }
      else {
        toSearchFor = MethodOrClosureScopeChooser.findVariableToUse(owner);
      }
      showDialogOrStartInplace(new IntroduceParameterInfoImpl(initialInfo, owner, toSearchFor), editor);
    }
    else {
      myEnclosingMethodsPopup = MethodOrClosureScopeChooser.create(scopes, editor, this, (owner, element) -> {
        showDialogOrStartInplace(new IntroduceParameterInfoImpl(initialInfo, owner, element), editor);
        return null;
      });
      myEnclosingMethodsPopup.showInBestPositionFor(editor);
    }
  }

  @NotNull
  private static List<GrParameterListOwner> findScopes(@NotNull InitialInfo initialInfo) {
    PsiElement place = initialInfo.getContext();
    final List<GrParameterListOwner> scopes = new ArrayList<>();
    while (true) {
      final GrParameterListOwner parent = PsiTreeUtil.getParentOfType(place, GrParameterListOwner.class);
      if (parent == null) break;
      scopes.add(parent);
      place = parent;
    }
    return scopes;
  }

  @Override
  public JBPopup get() {
    return myEnclosingMethodsPopup;
  }


  //method to hack in tests
  protected void showDialogOrStartInplace(@NotNull final IntroduceParameterInfo info, @NotNull final Editor editor) {
    if (isInplace(info, editor)) {
      final GrIntroduceContext context = createContext(info, editor);
      Map<OccurrencesChooser.ReplaceChoice, List<Object>> occurrencesMap = GrIntroduceHandlerBase.fillChoice(context);
      new IntroduceOccurrencesChooser(editor).showChooser(new Pass<>() {
        @Override
        public void pass(OccurrencesChooser.ReplaceChoice choice) {
          startInplace(info, context, choice);
        }
      }, occurrencesMap);
    }
    else {
      showDialog(info);
    }
  }

  protected void showDialog(IntroduceParameterInfo info) {
    new GrIntroduceParameterDialog(info).show();
  }

  private static void startInplace(@NotNull final IntroduceParameterInfo info,
                                   @NotNull final GrIntroduceContext context,
                                   OccurrencesChooser.ReplaceChoice replaceChoice) {
    new GrInplaceParameterIntroducer(info, context, replaceChoice).startInplaceIntroduceTemplate();
  }

  private static boolean isInplace(@NotNull IntroduceParameterInfo info,
                                   @NotNull Editor editor) {
    return GroovyIntroduceParameterUtil.findExpr(info) != null &&
           info.getToReplaceIn() instanceof GrMethod &&
           info.getToSearchFor() instanceof PsiMethod &&
           GrIntroduceHandlerBase.isInplace(editor, info.getContext());
  }

  @Override
  public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
    // Does nothing
  }

  private static GrIntroduceContext createContext(@NotNull IntroduceParameterInfo info,
                                                  @NotNull Editor editor) {
    GrExpression expr = GroovyIntroduceParameterUtil.findExpr(info);
    GrVariable var = GroovyIntroduceParameterUtil.findVar(info);
    StringPartInfo stringPart = info.getStringPartInfo();
    return new GrIntroduceVariableHandler().getContext(info.getProject(), editor, expr, var, stringPart, info.getToReplaceIn());
  }
}
