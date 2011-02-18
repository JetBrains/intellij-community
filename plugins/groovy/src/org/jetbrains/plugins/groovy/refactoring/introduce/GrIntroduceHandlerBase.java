/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.refactoring.introduce;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.IntroduceTargetChooser;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrForStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrWhileStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.refactoring.NameValidator;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public abstract class GrIntroduceHandlerBase<Settings extends GrIntroduceSettings> implements RefactoringActionHandler {
  protected abstract String getRefactoringName();

  protected abstract String getHelpID();

  @NotNull
  protected abstract PsiElement findScope(GrExpression expression);

  protected abstract void checkExpression(GrExpression selectedExpr) throws GrIntroduceRefactoringError;

  protected abstract GrIntroduceDialog<Settings> getDialog(GrIntroduceContext context);

  public abstract void runRefactoring(GrIntroduceContext context, Settings settings);

  public static List<GrExpression> collectExpressions(final PsiFile file, final Editor editor, final int offset) {
    Document document = editor.getDocument();
    CharSequence text = document.getCharsSequence();
    int correctedOffset = offset;
    int textLength = document.getTextLength();
    if (offset >= textLength) {
      correctedOffset = textLength - 1;
    }
    else if (!Character.isJavaIdentifierPart(text.charAt(offset))) {
      correctedOffset--;
    }
    if (correctedOffset < 0) {
      correctedOffset = offset;
    }
    else if (!Character.isJavaIdentifierPart(text.charAt(correctedOffset))) {
      if (text.charAt(correctedOffset) == ';') {//initially caret on the end of line
        correctedOffset--;
      }
      if (text.charAt(correctedOffset) != ')') {
        correctedOffset = offset;
      }
    }
    final PsiElement elementAtCaret = file.findElementAt(correctedOffset);
    final List<GrExpression> expressions = new ArrayList<GrExpression>();

    for (GrExpression expression = PsiTreeUtil.getParentOfType(elementAtCaret, GrExpression.class);
         expression != null;
         expression = PsiTreeUtil.getParentOfType(expression, GrExpression.class)) {
      if (expressions.contains(expression) || expression instanceof GrParenthesizedExpression) continue;
      if (expression instanceof GrSuperReferenceExpression || expression.getType() == PsiType.VOID) continue;

      if (expression instanceof GrApplicationStatement) continue;
      if (expression instanceof GrReferenceExpression &&
          (expression.getParent() instanceof GrMethodCall && ((GrReferenceExpression)expression).resolve() instanceof PsiMethod ||
           ((GrReferenceExpression)expression).resolve() instanceof PsiClass)) {
        continue;
      }
      if (expression instanceof GrAssignmentExpression) continue;

      expressions.add(expression);
    }
    return expressions;
  }

  public void invoke(final @NotNull Project project, final Editor editor, final PsiFile file, final @Nullable DataContext dataContext) {
    final SelectionModel selectionModel = editor.getSelectionModel();
    if (!selectionModel.hasSelection()) {
      final int offset = editor.getCaretModel().getOffset();

      final List<GrExpression> expressions = collectExpressions(file, editor, offset);
      if (expressions.isEmpty()) {
        selectionModel.selectLineAtCaret();
      }
      else if (expressions.size() == 1) {
        final TextRange textRange = expressions.get(0).getTextRange();
        selectionModel.setSelection(textRange.getStartOffset(), textRange.getEndOffset());
      }
      else {
        IntroduceTargetChooser.showChooser(editor, expressions,
                                           new Pass<GrExpression>() {
                                             public void pass(final GrExpression selectedValue) {
                                               invoke(project, editor, file, selectedValue.getTextRange().getStartOffset(),
                                                      selectedValue.getTextRange().getEndOffset());
                                             }
                                           },
                                           new Function<GrExpression, String>() {
                                             @Override
                                             public String fun(GrExpression grExpression) {
                                               return grExpression.getText();
                                             }
                                           });
        return;
      }
    }
    invoke(project, editor, file, selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    // Does nothing
  }

  public GrIntroduceContext getContext(Project project, Editor editor, GrExpression expression) {
    final PsiElement scope = findScope(expression);

    final PsiElement[] occurences = findOccurences(expression, scope);
    return new GrIntroduceContext(project, editor, expression, occurences, scope);
  }

  protected PsiElement[] findOccurences(GrExpression expression, PsiElement scope) {
    final PsiElement[] occurrences = GroovyRefactoringUtil.getExpressionOccurrences(PsiUtil.skipParentheses(expression, false), scope);
    if (occurrences == null || occurrences.length == 0) {
      throw new GrIntroduceRefactoringError(GroovyRefactoringBundle.message("no.occurences.found"));
    }
    return occurrences;
  }

  private boolean invoke(final Project project, final Editor editor, PsiFile file, int startOffset, int endOffset) {
    try {
      PsiDocumentManager.getInstance(project).commitAllDocuments();
      if (!(file instanceof GroovyFileBase)) {
        throw new GrIntroduceRefactoringError(GroovyRefactoringBundle.message("only.in.groovy.files"));
      }
      if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) {
        throw new GrIntroduceRefactoringError(RefactoringBundle.message("readonly.occurences.found"));
      }

      GrExpression selectedExpr = findExpression((GroovyFileBase)file, startOffset, endOffset);
      checkExpression(selectedExpr);

      final GrIntroduceContext context = getContext(project, editor, selectedExpr);
      final Settings settings = showDialog(context);
      if (settings == null) return false;

      CommandProcessor.getInstance().executeCommand(context.project, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            runRefactoring(context, settings);
          }
        });
      }
    }, getRefactoringName(), null);

      return true;
    }
    catch (GrIntroduceRefactoringError e) {
      CommonRefactoringUtil
        .showErrorHint(project, editor, RefactoringBundle.getCannotRefactorMessage(e.getMessage()), getRefactoringName(), getHelpID());
      return false;
    }
  }

  @NotNull
  public static GrExpression findExpression(GroovyFileBase file, int startOffset, int endOffset) {
    GrExpression selectedExpr = GroovyRefactoringUtil.findElementInRange(file, startOffset, endOffset, GrExpression.class);
    if (selectedExpr == null || (selectedExpr instanceof GrClosableBlock && selectedExpr.getParent() instanceof GrStringInjection)) {
      throw new GrIntroduceRefactoringError(GroovyRefactoringBundle.message("selected.block.should.represent.an.expression"));
    }

    if (selectedExpr instanceof GrReferenceExpression &&
        selectedExpr.getParent() instanceof GrMethodCall &&
        (((GrMethodCall)selectedExpr.getParent()).isCommandExpression() || selectedExpr.getParent() instanceof GrApplicationStatement) ||
        selectedExpr instanceof GrApplicationStatement) {
      throw new GrIntroduceRefactoringError(GroovyRefactoringBundle.message("selected.expression.in.command.expression"));
    }

    PsiType type = selectedExpr.getType();
    if (type != null) type = TypeConversionUtil.erasure(type);

    if (PsiType.VOID.equals(type)) {
      throw new GrIntroduceRefactoringError(GroovyRefactoringBundle.message("selected.expression.has.void.type"));
    }
    return selectedExpr;
  }

  @Nullable
  private Settings showDialog(GrIntroduceContext context) {

    // Add occurences highlighting
    ArrayList<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();
    HighlightManager highlightManager = null;
    if (context.editor != null) {
      highlightManager = HighlightManager.getInstance(context.project);
      EditorColorsManager colorsManager = EditorColorsManager.getInstance();
      TextAttributes attributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
      if (context.occurrences.length > 1) {
        highlightManager.addOccurrenceHighlights(context.editor, context.occurrences, attributes, true, highlighters);
      }
    }

    GrIntroduceDialog<Settings> dialog = getDialog(context);

    dialog.show();
    if (dialog.isOK()) {
      if (context.editor != null) {
        for (RangeHighlighter highlighter : highlighters) {
          highlightManager.removeSegmentHighlighter(context.editor, highlighter);
        }
      }
      return dialog.getSettings();
    }
    else {
      if (context.occurrences.length > 1) {
        WindowManager.getInstance().getStatusBar(context.project)
          .setInfo(GroovyRefactoringBundle.message("press.escape.to.remove.the.highlighting"));
      }
    }
    return null;
  }

  @Nullable
  public static PsiElement findAnchor(GrIntroduceContext context,
                                       GrIntroduceSettings settings,
                                       PsiElement[] occurrences,
                                       final PsiElement container) {
    if (occurrences.length == 0) return null;
    PsiElement candidate;
    if (occurrences.length == 1 || !settings.replaceAllOccurrences()) {
      candidate = context.expression;
    }
    else {
      GroovyRefactoringUtil.sortOccurrences(occurrences);
      candidate = occurrences[0];
    }
    while (candidate != null && !container.equals(candidate.getParent())) {
      candidate = candidate.getParent();
    }
    if (candidate == null) {
      return null;
    }
    if ((container instanceof GrWhileStatement) &&
        candidate.equals(((GrWhileStatement)container).getCondition())) {
      return container;
    }
    if ((container instanceof GrIfStatement) &&
        candidate.equals(((GrIfStatement)container).getCondition())) {
      return container;
    }
    if ((container instanceof GrForStatement) &&
        candidate.equals(((GrForStatement)container).getClause())) {
      return container;
    }
    return candidate;
  }


  public interface Validator extends NameValidator {
    boolean isOK(GrIntroduceDialog dialog);
  }
}
