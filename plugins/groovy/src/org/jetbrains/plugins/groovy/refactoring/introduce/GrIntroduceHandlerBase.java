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
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.IntroduceTargetChooser;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.refactoring.NameValidator;
import org.jetbrains.plugins.groovy.refactoring.introduce.field.GrIntroduceFieldHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public abstract class GrIntroduceHandlerBase<Settings extends GrIntroduceSettings> implements RefactoringActionHandler {
  protected abstract String getRefactoringName();

  protected abstract String getHelpID();

  @NotNull
  protected abstract PsiElement findScope(GrExpression expression, GrVariable variable);

  protected abstract void checkExpression(GrExpression selectedExpr) throws GrIntroduceRefactoringError;

  protected abstract void checkVariable(GrVariable variable) throws GrIntroduceRefactoringError;

  protected abstract void checkOccurrences(PsiElement[] occurrences);

  protected abstract GrIntroduceDialog<Settings> getDialog(GrIntroduceContext context);

  @Nullable
  public abstract GrVariable runRefactoring(GrIntroduceContext context, Settings settings);

  public static List<GrExpression> collectExpressions(final PsiFile file, final Editor editor, final int offset) {
    int correctedOffset = correctOffset(editor, offset);
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

  private static int correctOffset(Editor editor, int offset) {
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
    return correctedOffset;
  }

  @Nullable
  private static GrVariable findVariableAtCaret(final PsiFile file, final Editor editor, final int offset) {
    final int correctOffset = correctOffset(editor, offset);
    final PsiElement elementAtCaret = file.findElementAt(correctOffset);
    final GrVariable variable = PsiTreeUtil.getParentOfType(elementAtCaret, GrVariable.class);
    if (variable != null && variable.getNameIdentifierGroovy().getTextRange().contains(correctOffset)) return variable;
    return null;
  }

  public void invoke(final @NotNull Project project, final Editor editor, final PsiFile file, final @Nullable DataContext dataContext) {
    final SelectionModel selectionModel = editor.getSelectionModel();
    if (!selectionModel.hasSelection()) {
      final int offset = editor.getCaretModel().getOffset();

      final List<GrExpression> expressions = collectExpressions(file, editor, offset);
      if (expressions.isEmpty()) {
        final GrVariable variable = findVariableAtCaret(file, editor, offset);
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

  public GrIntroduceContext getContext(Project project, Editor editor, GrExpression expression, @Nullable GrVariable variable) {
    final PsiElement scope = findScope(expression, variable);

    if (variable == null) {
      final PsiElement[] occurences = findOccurences(expression, scope);
      return new GrIntroduceContext(project, editor, expression, occurences, scope, variable);

    }
    else {
      final List<PsiElement> list = new ArrayList<PsiElement>();
      ReferencesSearch.search(variable, new LocalSearchScope(scope)).forEach(new Processor<PsiReference>() {
        @Override
        public boolean process(PsiReference psiReference) {
          final PsiElement element = psiReference.getElement();
          if (element != null) {
            list.add(element);
          }
          return true;
        }
      });
      return new GrIntroduceContext(project, editor, variable.getInitializerGroovy(), list.toArray(new PsiElement[list.size()]), scope,
                                    variable);
    }
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
      final GrVariable variable = findVariable((GroovyFile)file, startOffset, endOffset);
      if (variable != null) {
        checkVariable(variable);
      }
      else if (selectedExpr != null) {
        checkExpression(selectedExpr);
      }
      else {
        throw new GrIntroduceRefactoringError(null);
      }

      final GrIntroduceContext context = getContext(project, editor, selectedExpr, variable);
      checkOccurrences(context.occurrences);
      final Settings settings = showDialog(context);
      if (settings == null) return false;

      CommandProcessor.getInstance().executeCommand(context.project, new Runnable() {
      public void run() {
        final GrVariable created = ApplicationManager.getApplication().runWriteAction(new Computable<GrVariable>() {
          public GrVariable compute() {
            return runRefactoring(context, settings);
          }
        });

        if (editor != null && created != null) {
          PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
          editor.getCaretModel().moveToOffset(created.getNameIdentifierGroovy().getTextOffset());
        }
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

  @Nullable
  private static GrVariable findVariable(GroovyFile file, int startOffset, int endOffset) {
    GrVariable var = GroovyRefactoringUtil.findElementInRange(file, startOffset, endOffset, GrVariable.class);
    if (var == null) {
      final GrVariableDeclaration variableDeclaration =
        GroovyRefactoringUtil.findElementInRange(file, startOffset, endOffset, GrVariableDeclaration.class);
      if (variableDeclaration == null) return null;
      final GrVariable[] variables = variableDeclaration.getVariables();
      if (variables.length == 1) {
        var = variables[0];
      }
    }
    if (var instanceof GrParameter || var instanceof GrField) {
      return null;
    }
    return var;
  }

  @Nullable
  public static GrExpression findExpression(GroovyFileBase file, int startOffset, int endOffset) {
    GrExpression selectedExpr = GroovyRefactoringUtil.findElementInRange(file, startOffset, endOffset, GrExpression.class);
    if (selectedExpr == null) return null;
    if (selectedExpr instanceof GrClosableBlock && selectedExpr.getParent() instanceof GrStringInjection) {
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

  protected static void deleteLocalVar(GrIntroduceContext context) {
    final GrVariable resolved = GrIntroduceFieldHandler.resolveLocalVar(context);
    final PsiElement parent = resolved.getParent();
    if (parent instanceof GrTupleDeclaration) {
      if (((GrTupleDeclaration)parent).getVariables().length == 1) {
        parent.getParent().delete();
      }
      else {
        final GrExpression initializerGroovy = resolved.getInitializerGroovy();
        if (initializerGroovy != null) initializerGroovy.delete();
        resolved.delete();
      }
    }
    else {
      if (((GrVariableDeclaration)parent).getVariables().length == 1) {
        parent.delete();
      }
      else {
        resolved.delete();
      }
    }
  }

  protected static GrVariable resolveLocalVar(GrIntroduceContext context) {
    if (context.var != null) return context.var;
    return (GrVariable)((GrReferenceExpression)context.expression).resolve();
  }

  public static boolean hasLhs(final PsiElement[] occurrences) {
    for (PsiElement element : occurrences) {
      if (element instanceof GrReferenceExpression) {
        if (PsiUtil.isLValue((GroovyPsiElement)element)) return true;
        if (ControlFlowUtils.isIncOrDecOperand((GrReferenceExpression)element)) return true;
      }
    }
    return false;
  }


  public interface Validator extends NameValidator {
    boolean isOK(GrIntroduceDialog dialog);
  }
}
