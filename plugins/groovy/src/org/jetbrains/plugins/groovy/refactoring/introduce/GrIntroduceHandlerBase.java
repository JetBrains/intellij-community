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
package org.jetbrains.plugins.groovy.refactoring.introduce;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
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
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GrRefactoringError;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.refactoring.NameValidator;

import java.util.*;

/**
 * @author Maxim.Medvedev
 */
public abstract class GrIntroduceHandlerBase<Settings extends GrIntroduceSettings> implements RefactoringActionHandler {
  public static final Function<GrExpression, String> GR_EXPRESSION_RENDERER = new Function<GrExpression, String>() {
    @Override
    public String fun(@NotNull GrExpression expr) {
      return expr.getText();
    }
  };

  @NotNull
  protected abstract String getRefactoringName();

  @NotNull
  protected abstract String getHelpID();

  @NotNull
  protected abstract PsiElement findScope(GrExpression expression, GrVariable variable, StringPartInfo stringPart);

  protected abstract void checkExpression(@NotNull GrExpression selectedExpr) throws GrRefactoringError;

  protected abstract void checkVariable(@NotNull GrVariable variable) throws GrRefactoringError;

  protected abstract void checkStringLiteral(@NotNull StringPartInfo info) throws GrRefactoringError;

  protected abstract void checkOccurrences(@NotNull PsiElement[] occurrences);

  @NotNull
  protected abstract GrIntroduceDialog<Settings> getDialog(@NotNull GrIntroduceContext context);

  @Nullable
  public abstract GrVariable runRefactoring(@NotNull GrIntroduceContext context, @NotNull Settings settings);

  protected abstract GrInplaceIntroducer getIntroducer(@NotNull GrVariable var,
                                                       @NotNull GrIntroduceContext context,
                                                       @NotNull Settings settings,
                                                       @NotNull List<RangeMarker> occurrenceMarkers,
                                                       RangeMarker varRangeMarker,
                                                       @Nullable RangeMarker expressionRangeMarker,
                                                       @Nullable RangeMarker stringPartRangeMarker);

  protected abstract Settings getSettingsForInplace(GrIntroduceContext context, OccurrencesChooser.ReplaceChoice choice);

  protected Map<OccurrencesChooser.ReplaceChoice, List<Object>> fillChoice(GrIntroduceContext context) {
    HashMap<OccurrencesChooser.ReplaceChoice, List<Object>> map = ContainerUtil.newLinkedHashMap();

    if (context.getExpression() != null) {
      map.put(OccurrencesChooser.ReplaceChoice.NO, Collections.<Object>singletonList(context.getExpression()));
    }
    else if (context.getStringPart() != null) {
      map.put(OccurrencesChooser.ReplaceChoice.NO, Collections.<Object>singletonList(context.getStringPart()));
    }

    PsiElement[] occurrences = context.getOccurrences();
    if (occurrences.length > 1) {
      map.put(OccurrencesChooser.ReplaceChoice.ALL, Arrays.<Object>asList(occurrences));
    }
    return map;
  }

  @NotNull
  public static List<GrExpression> collectExpressions(final PsiFile file, final Editor editor, final int offset, boolean acceptVoidCalls) {
    int correctedOffset = correctOffset(editor, offset);
    final PsiElement elementAtCaret = file.findElementAt(correctedOffset);
    final List<GrExpression> expressions = new ArrayList<GrExpression>();

    for (GrExpression expression = PsiTreeUtil.getParentOfType(elementAtCaret, GrExpression.class);
         expression != null;
         expression = PsiTreeUtil.getParentOfType(expression, GrExpression.class)) {
      if (expressions.contains(expression)) continue;
      if (expression instanceof GrParenthesizedExpression && !expressions.contains(((GrParenthesizedExpression)expression).getOperand())) {
        expressions.add(((GrParenthesizedExpression)expression).getOperand());
      }
      if (expressionIsIncorrect(expression, acceptVoidCalls)) continue;

      expressions.add(expression);
    }
    return expressions;
  }

  public static boolean expressionIsIncorrect(@Nullable GrExpression expression, boolean acceptVoidCalls) {
    if (expression instanceof GrParenthesizedExpression) return true;
    if (PsiUtil.isSuperReference(expression)) return true;
    if (expression instanceof GrAssignmentExpression) return true;
    if (expression instanceof GrReferenceExpression && expression.getParent() instanceof GrCall) {
      final GroovyResolveResult resolveResult = ((GrReferenceExpression)expression).advancedResolve();
      final PsiElement resolved = resolveResult.getElement();
      return resolved instanceof PsiMethod && !resolveResult.isInvokedOnProperty() || resolved instanceof PsiClass;
    }

    if (expression instanceof GrClosableBlock && expression.getParent() instanceof GrStringInjection) return true;
    if (!acceptVoidCalls && expression instanceof GrMethodCall && PsiType.VOID == expression.getType()) return true;

    return false;
  }

  public static int correctOffset(Editor editor, int offset) {
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
    else {
      char c = text.charAt(correctedOffset);
      if (c == ';' && correctedOffset != 0) {//initially caret on the end of line
        correctedOffset--;
      }
      else if (!Character.isJavaIdentifierPart(c) && c != ')' && c != ']' && c != '}' && c != '\'' && c != '"' && c != '/') {
        correctedOffset = offset;
      }
    }
    return correctedOffset;
  }

  @Nullable
  public static GrVariable findVariableAtCaret(final PsiFile file, final Editor editor, final int offset) {
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

      final List<GrExpression> expressions = collectExpressions(file, editor, offset, false);
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
        IntroduceTargetChooser.showChooser(editor, expressions, new Pass<GrExpression>() {
          public void pass(final GrExpression selectedValue) {
            invoke(project, editor, file, selectedValue.getTextRange().getStartOffset(), selectedValue.getTextRange().getEndOffset());
          }
        }, GR_EXPRESSION_RENDERER);
        return;
      }
    }
    invoke(project, editor, file, selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    // Does nothing
  }

  @NotNull
  public GrIntroduceContext getContext(@NotNull Project project,
                                       @NotNull Editor editor,
                                       @Nullable GrExpression expression,
                                       @Nullable GrVariable variable,
                                       @Nullable StringPartInfo stringPart) {
    final PsiElement scope = findScope(expression, variable, stringPart);

    if (variable != null) {
      final List<PsiElement> list = Collections.synchronizedList(new ArrayList<PsiElement>());
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
      final PsiElement[] occurrences = list.toArray(new PsiElement[list.size()]);
      return new GrIntroduceContextImpl(project, editor, variable.getInitializerGroovy(), variable, stringPart, occurrences, scope);
    }
    else if (expression != null ) {
      final PsiElement[] occurrences = findOccurrences(expression, scope);
      return new GrIntroduceContextImpl(project, editor, expression, variable, stringPart, occurrences, scope);
    }
    else {
      assert stringPart != null;
      return new GrIntroduceContextImpl(project, editor, expression, variable, stringPart, new PsiElement[]{stringPart.getLiteral()}, scope);
    }
  }

  @NotNull
  protected PsiElement[] findOccurrences(@NotNull GrExpression expression, @NotNull PsiElement scope) {
    final PsiElement[] occurrences = GroovyRefactoringUtil.getExpressionOccurrences(PsiUtil.skipParentheses(expression, false), scope);
    if (occurrences == null || occurrences.length == 0) {
      throw new GrRefactoringError(GroovyRefactoringBundle.message("no.occurrences.found"));
    }
    return occurrences;
  }

  private boolean invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull PsiFile file, int startOffset, int endOffset) {
    try {
      PsiDocumentManager.getInstance(project).commitAllDocuments();
      if (!(file instanceof GroovyFileBase)) {
        throw new GrRefactoringError(GroovyRefactoringBundle.message("only.in.groovy.files"));
      }
      if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) {
        throw new GrRefactoringError(RefactoringBundle.message("readonly.occurences.found"));
      }

      GrExpression selectedExpr = findExpression(file, startOffset, endOffset);
      final GrVariable variable = findVariable(file, startOffset, endOffset);
      final StringPartInfo stringPart = StringPartInfo.findStringPart(file, startOffset, endOffset);
      if (variable != null) {
        checkVariable(variable);
      }
      else if (selectedExpr != null) {
        checkExpression(selectedExpr);
      }
      else if (stringPart != null) {
        checkStringLiteral(stringPart);
      }
      else {
        throw new GrRefactoringError(null);
      }

      final GrIntroduceContext context = getContext(project, editor, selectedExpr, variable, stringPart);
      if (!CommonRefactoringUtil.checkReadOnlyStatus(project, context.getOccurrences())) {
        return false;
      }
      checkOccurrences(context.getOccurrences());


      final boolean isInplace = isInplace(context);
      Pass<OccurrencesChooser.ReplaceChoice> callback = new Pass<OccurrencesChooser.ReplaceChoice>() {
        @Override
        public void pass(final OccurrencesChooser.ReplaceChoice choice) {

          final Settings settings = isInplace ? getSettingsForInplace(context, choice) : showDialog(context);
          if (settings == null) return;

          CommandProcessor.getInstance().executeCommand(project, new Runnable() {
            public void run() {
              List<RangeMarker> occurrences = ContainerUtil.newArrayList();
              Document document = editor.getDocument();
              for (PsiElement element : context.getOccurrences()) {
                occurrences.add(createRange(document, element));
              }
              RangeMarker expressionRangeMarker = createRange(document, context.getExpression());
              RangeMarker stringPartRangeMarker = createRange(document, context.getStringPart());
              RangeMarker varRangeMarker = createRange(document, context.getVar());

              GrVariable var = ApplicationManager.getApplication().runWriteAction(new Computable<GrVariable>() {
                @Override
                public GrVariable compute() {
                  return runRefactoring(context, settings);
                }
              });

              if (isInplace && var != null) {
                GrInplaceIntroducer introducer = getIntroducer(var, context, settings, occurrences, varRangeMarker, expressionRangeMarker, stringPartRangeMarker);
                PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());
                introducer.performInplaceRefactoring(introducer.suggestNames(context));
              }
            }
          }, getRefactoringName(), getRefactoringName());
        }
      };

      if (isInplace(context)) {
        Map<OccurrencesChooser.ReplaceChoice, List<Object>> occurrencesMap = fillChoice(context);
        new OccurrencesChooser<Object>(editor) {
          @Override
          protected TextRange getOccurrenceRange(Object occurrence) {
            if (occurrence instanceof PsiElement) {
              return ((PsiElement)occurrence).getTextRange();
            }
            else if (occurrence instanceof StringPartInfo) {
              return ((StringPartInfo)occurrence).getRange();
            }
            else {
              return null;
            }
          }
        }.showChooser(callback, occurrencesMap);
      }
      else {
        callback.pass(null);
      }

      return true;
    }
    catch (GrRefactoringError e) {
      CommonRefactoringUtil.showErrorHint(project, editor, RefactoringBundle.getCannotRefactorMessage(e.getMessage()), getRefactoringName(), getHelpID());
      return false;
    }
  }

  private static RangeMarker createRange(Document document, StringPartInfo part) {
    if (part == null) {
      return null;
    }
    TextRange range = part.getRange().shiftRight(part.getLiteral().getTextRange().getStartOffset());
    return document.createRangeMarker(range.getStartOffset(), range.getEndOffset(), true);

  }

  @Nullable
  private static RangeMarker createRange(@NotNull Document document, @Nullable PsiElement expression) {
    if (expression == null) {
      return null;
    }
    TextRange range = expression.getTextRange();
    return document.createRangeMarker(range.getStartOffset(), range.getEndOffset(), false);
  }


  protected boolean isInplace(GrIntroduceContext context) {
    final RefactoringSupportProvider supportProvider = LanguageRefactoringSupport.INSTANCE.forLanguage(context.getPlace().getLanguage());
    return supportProvider != null &&
           context.getEditor().getSettings().isVariableInplaceRenameEnabled() &&
           supportProvider.isInplaceIntroduceAvailable(context.getPlace(), context.getPlace()) &&
           !ApplicationManager.getApplication().isUnitTestMode();
  }

  @Nullable
  public static GrVariable findVariable(@NotNull PsiFile file, int startOffset, int endOffset) {
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
  public static GrVariable findVariable(@NotNull GrStatement statement) {
    if (!(statement instanceof GrVariableDeclaration)) return null;
    final GrVariableDeclaration variableDeclaration = (GrVariableDeclaration)statement;
    final GrVariable[] variables = variableDeclaration.getVariables();

    GrVariable var = null;
    if (variables.length == 1) {
      var = variables[0];
    }
    if (var instanceof GrParameter || var instanceof GrField) {
      return null;
    }
    return var;
  }


  @Nullable
  public static GrExpression findExpression(PsiFile file, int startOffset, int endOffset) {
    GrExpression selectedExpr = GroovyRefactoringUtil.findElementInRange(file, startOffset, endOffset, GrExpression.class);
    return findExpression(selectedExpr);
  }

  @Nullable
  public static GrExpression findExpression(GrStatement selectedExpr) {
    if (!(selectedExpr instanceof GrExpression)) return null;

    GrExpression selected = (GrExpression)selectedExpr;
    while (selected instanceof GrParenthesizedExpression) selected = ((GrParenthesizedExpression)selected).getOperand();
    if (selected == null) return null;
    PsiType type = selected.getType();
    if (type != null) type = TypeConversionUtil.erasure(type);

    if (PsiType.VOID.equals(type)) {
      return null;
    }

    return selected;
  }

  @Nullable
  private Settings showDialog(@NotNull GrIntroduceContext context) {

    // Add occurrences highlighting
    ArrayList<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();
    HighlightManager highlightManager = null;
    if (context.getEditor() != null) {
      highlightManager = HighlightManager.getInstance(context.getProject());
      EditorColorsManager colorsManager = EditorColorsManager.getInstance();
      TextAttributes attributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
      if (context.getOccurrences().length > 1) {
        highlightManager.addOccurrenceHighlights(context.getEditor(), context.getOccurrences(), attributes, true, highlighters);
      }
    }

    GrIntroduceDialog<Settings> dialog = getDialog(context);

    dialog.show();
    if (dialog.isOK()) {
      if (context.getEditor() != null) {
        for (RangeHighlighter highlighter : highlighters) {
          highlightManager.removeSegmentHighlighter(context.getEditor(), highlighter);
        }
      }
      return dialog.getSettings();
    }
    else {
      if (context.getOccurrences().length > 1) {
        WindowManager.getInstance().getStatusBar(context.getProject())
          .setInfo(GroovyRefactoringBundle.message("press.escape.to.remove.the.highlighting"));
      }
    }
    return null;
  }

  @Nullable
  public static PsiElement findAnchor(@NotNull PsiElement[] occurrences,
                                      @NotNull PsiElement container) {
    if (occurrences.length == 0) return null;

    PsiElement candidate;
    if (occurrences.length == 1) {
      candidate = occurrences[0];
      candidate = findContainingStatement(candidate);
    }
    else {
      candidate = occurrences[0];
      while (candidate != null && candidate.getParent() != container) {
        candidate = candidate.getParent();
      }
    }

    final GrStringInjection injection = PsiTreeUtil.getParentOfType(candidate, GrStringInjection.class);
    if (injection != null && !injection.getText().contains("\n")) {
      candidate = findContainingStatement(injection);
    }

    if (candidate == null) return null;

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

    while (candidate instanceof GrIfStatement &&
           candidate.getParent() instanceof GrIfStatement &&
           ((GrIfStatement)candidate.getParent()).getElseBranch() == candidate) {
      candidate = candidate.getParent();
    }
    return candidate;
  }

  @Nullable
  private static PsiElement findContainingStatement(@Nullable PsiElement candidate) {
    while (candidate != null && !PsiUtil.isExpressionStatement(candidate)) {
      candidate = candidate.getParent();
      if (candidate instanceof GrCaseLabel) candidate = candidate.getParent();
    }
    return candidate;
  }

  public static void deleteLocalVar(@NotNull GrIntroduceContext context) {
    final GrVariable resolved = resolveLocalVar(context);

    final PsiElement parent = resolved.getParent();
    if (((GrVariableDeclaration)parent).getVariables().length == 1) {
      parent.delete();
    }
    else {
      GrExpression initializer = resolved.getInitializerGroovy();
      if (initializer != null) initializer.delete(); //don't special check for tuple, but this line is for the tuple case
      resolved.delete();
    }
  }

  @NotNull
  public static GrVariable resolveLocalVar(@NotNull GrIntroduceContext context) {
    final GrVariable var = context.getVar();
    if (var != null) {
      return var;
    }

    final GrReferenceExpression expression = (GrReferenceExpression)context.getExpression();
    assert expression != null;

    final PsiElement resolved = expression.resolve();
    assert resolved instanceof GrVariable;
    return (GrVariable)resolved;
  }

  public static boolean hasLhs(@NotNull final PsiElement[] occurrences) {
    for (PsiElement element : occurrences) {
      if (element instanceof GrReferenceExpression) {
        if (PsiUtil.isLValue((GroovyPsiElement)element)) return true;
        if (ControlFlowUtils.isIncOrDecOperand((GrReferenceExpression)element)) return true;
      }
    }
    return false;
  }

  @NotNull
  public static PsiElement getCurrentPlace(@Nullable GrExpression expr,
                                           @Nullable GrVariable var,
                                           @Nullable StringPartInfo stringPartInfo) {
    if (var != null) return var;
    if (expr != null) return expr;
    if (stringPartInfo != null) return stringPartInfo.getLiteral();

    throw new IncorrectOperationException();
  }

  @NotNull
  public static GrExpression generateExpressionFromStringPart(final StringPartInfo stringPart, final Project project) {
    Data data = new Data(stringPart);
    String startQuote = data.getStartQuote();
    TextRange range = data.getRange();
    String literalText = data.getText();
    String endQuote = data.getEndQuote();

    final String substringLiteral = startQuote + range.substring(literalText) + endQuote;
    return GroovyPsiElementFactory.getInstance(project).createExpressionFromText(substringLiteral);
  }

  @NotNull
  public static GrExpression processLiteral(final String varName, final StringPartInfo stringPart, final Project project) {
    Data data = new Data(stringPart);
    String startQuote = data.getStartQuote();
    TextRange range = data.getRange();
    String literalText = data.getText();
    String endQuote = data.getEndQuote();

    String prefix = literalText.substring(0, range.getStartOffset()) ;
    String suffix =  literalText.substring(range.getEndOffset());

    StringBuilder buffer = new StringBuilder();
    if (!prefix.equals(startQuote)) {
      buffer.append(prefix).append(endQuote).append('+');
    }
    buffer.append(varName);
    if (!suffix.equals(endQuote)) {
      buffer.append('+').append(startQuote).append(suffix);
    }

    final GrExpression concatenation = GroovyPsiElementFactory.getInstance(project).createExpressionFromText(buffer);

    final GrExpression concat = stringPart.getLiteral().replaceWithExpression(concatenation, false);
    if (concat instanceof GrReferenceExpression) {
      return concat;
    }
    else {
      assert concat instanceof GrBinaryExpression;
      final GrExpression left = ((GrBinaryExpression)concat).getLeftOperand();
      if (left instanceof GrReferenceExpression) {
        return left;
      }
      else {
        assert left instanceof GrBinaryExpression;
        final GrExpression right = ((GrBinaryExpression)left).getRightOperand();
        assert right != null;
        return right;
      }
    }
  }

  public interface Validator extends NameValidator {
    boolean isOK(GrIntroduceDialog dialog);
  }

  private static class Data {
    private String myText;
    private String myStartQuote;
    private String myEndQuote;
    private TextRange myRange;

    public Data(final StringPartInfo stringPartInfo) {
      assert stringPartInfo != null;

      final GrLiteral literal = stringPartInfo.getLiteral();

      myText = literal.getText();

      myStartQuote = GrStringUtil.getStartQuote(myText);
      myEndQuote = GrStringUtil.getEndQuote(myText);
      final TextRange dataRange = new TextRange(myStartQuote.length(), myText.length() - myEndQuote.length());

      myRange = stringPartInfo.getRange().intersection(dataRange);
    }

    public String getText() {
      return myText;
    }

    public String getStartQuote() {
      return myStartQuote;
    }

    public String getEndQuote() {
      return myEndQuote;
    }

    public TextRange getRange() {
      return myRange;
    }
  }
}
