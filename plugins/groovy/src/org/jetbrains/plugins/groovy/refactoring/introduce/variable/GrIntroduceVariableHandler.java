// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.introduce.variable;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.formatter.GrControlStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.refactoring.GrRefactoringError;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;

import java.util.function.Consumer;

import static org.jetbrains.annotations.Nls.Capitalization.Title;

public class GrIntroduceVariableHandler extends GrIntroduceHandlerBase<GroovyIntroduceVariableSettings, GrControlFlowOwner> {
  public static final String DUMMY_NAME = "________________xxx_________________";
  private RangeMarker myPosition = null;

  @Override
  protected GrControlFlowOwner @NotNull [] findPossibleScopes(GrExpression selectedExpr,
                                                              GrVariable variable,
                                                              StringPartInfo stringPartInfo,
                                                              Editor editor) {
    // Get container element
    final GrControlFlowOwner scope = ControlFlowUtils.findControlFlowOwner(stringPartInfo != null ? stringPartInfo.getLiteral() : selectedExpr);
    if (scope == null) {
      throw new GrRefactoringError(
        GroovyRefactoringBundle.message("refactoring.is.not.supported.in.the.current.context", getRefactoringNameText()));
    }
    if (!GroovyRefactoringUtil.isAppropriateContainerForIntroduceVariable(scope)) {
      throw new GrRefactoringError(
        GroovyRefactoringBundle.message("refactoring.is.not.supported.in.the.current.context", getRefactoringNameText()));
    }
    return new GrControlFlowOwner[]{scope};
  }

  @Override
  protected void checkExpression(@NotNull GrExpression selectedExpr) {
    // Cannot perform refactoring in parameter default values

    PsiElement parent = selectedExpr.getParent();
    while (!(parent == null || parent instanceof GroovyFileBase || parent instanceof GrParameter)) {
      parent = parent.getParent();
    }

    if (checkInFieldInitializer(selectedExpr)) {
      throw new GrRefactoringError(GroovyRefactoringBundle.message("refactoring.is.not.supported.in.the.current.context"));
    }

    if (parent instanceof GrParameter) {
      throw new GrRefactoringError(GroovyRefactoringBundle.message("refactoring.is.not.supported.in.method.parameters"));
    }
  }

  @Override
  protected void checkVariable(@NotNull GrVariable variable) throws GrRefactoringError {
    throw new GrRefactoringError(null);
  }

  @Override
  protected void checkStringLiteral(@NotNull StringPartInfo info) throws GrRefactoringError {
    //todo
  }

  @Override
  protected void checkOccurrences(PsiElement @NotNull [] occurrences) {
    //nothing to do
  }

  private static boolean checkInFieldInitializer(@NotNull GrExpression expr) {
    PsiElement parent = expr.getParent();
    if (parent instanceof GrClosableBlock) {
      return false;
    }
    else if (parent instanceof GrField field && expr == field.getInitializerGroovy()) {
      return true;
    }
    else if (parent instanceof GrExpression expression) {
      return checkInFieldInitializer(expression);
    }
    return false;
  }

  /**
   * Inserts new variable declarations and replaces occurrences
   */
  @Override
  public GrVariable runRefactoring(@NotNull GrIntroduceContext context, @NotNull GroovyIntroduceVariableSettings settings) {
    // Generating variable declaration

    GrVariable insertedVar = processExpression(context, settings);
    moveOffsetToPositionMarker(context.getEditor());
    return insertedVar;
  }

  private void moveOffsetToPositionMarker(Editor editor) {
    if (editor != null && getPositionMarker() != null) {
      editor.getSelectionModel().removeSelection();
      editor.getCaretModel().moveToOffset(getPositionMarker().getEndOffset());
    }
  }

  @Override
  protected GrInplaceVariableIntroducer getIntroducer(@NotNull GrIntroduceContext context, @NotNull OccurrencesChooser.ReplaceChoice choice) {

    final Ref<GrIntroduceContext> contextRef = Ref.create(context);

    if (context.getStringPart() != null) {
      extractStringPart(contextRef);
    }

    context = contextRef.get();

    final GrStatement anchor = findAnchor(context, choice == OccurrencesChooser.ReplaceChoice.ALL);

    if (anchor.getParent() instanceof GrControlStatement) {
      addBraces(anchor, contextRef);
    }

    return new GrInplaceVariableIntroducer(getRefactoringName(), choice, contextRef.get()) {
      @Override
      protected GrVariable runRefactoring(GrIntroduceContext context, GroovyIntroduceVariableSettings settings, boolean processUsages) {
        return refactorInWriteAction(() -> processUsages ? processExpression(context, settings) : addVariable(context, settings));
      }

      @Override
      protected void performPostIntroduceTasks() {
        super.performPostIntroduceTasks();
        moveOffsetToPositionMarker(contextRef.get().getEditor());
      }
    };
  }

  private static GrVariable addVariable(@NotNull GrIntroduceContext context, @NotNull GroovyIntroduceVariableSettings settings) {
    GrStatement anchor = findAnchor(context, settings.replaceAllOccurrences());
    PsiElement parent = anchor.getParent();
    assert parent instanceof GrStatementOwner;
    GrVariableDeclaration generated = generateDeclaration(context, settings);
    GrStatement declaration = ((GrStatementOwner)parent).addStatementBefore(generated, anchor);
    declaration = (GrStatement)JavaCodeStyleManager.getInstance(context.getProject()).shortenClassReferences(declaration);

    PsiDocumentManager.getInstance(context.getProject()).doPostponedOperationsAndUnblockDocument(context.getEditor().getDocument());

    return ((GrVariableDeclaration)declaration).getVariables()[0];
  }

  @Override
  protected void showScopeChooser(GrControlFlowOwner[] scopes, Consumer<? super GrControlFlowOwner> callback, Editor editor) {
    //todo do nothing right now
  }

  private static @NotNull GrVariableDeclaration generateDeclaration(@NotNull GrIntroduceContext context,
                                                                    @NotNull GroovyIntroduceVariableSettings settings) {
    PsiType type = settings.getSelectedType();
    final String[] modifiers;
    if (type != null) {
      String text = type.getCanonicalText();
      modifiers = !text.equals(GrModifier.VAL) && !text.equals(PsiModifier.FINAL) && settings.isDeclareFinal()
                  ? new String[]{PsiModifier.FINAL}
                  : null;
    }
    else if (settings.isDeclareFinal()) {
      modifiers = new String[]{PsiModifier.FINAL};
    }
    else {
      modifiers = null;
    }

    final GrVariableDeclaration declaration =
      GroovyPsiElementFactory.getInstance(context.getProject()).createVariableDeclaration(modifiers, "foo", type, settings.getName());

    @NotNull GrVariable variable = declaration.getVariables()[0];
    final GrExpression initializer = context.getStringPart() != null
                                     ? context.getStringPart().createLiteralFromSelected()
                                     : context.getExpression();
    assert initializer != null;
    final GrExpression dummyInitializer = variable.getInitializerGroovy();
    assert dummyInitializer != null;
    dummyInitializer.replaceWithExpression(initializer, true);
    return declaration;
  }

  private @NotNull GrVariable processExpression(@NotNull GrIntroduceContext context,
                                                @NotNull GroovyIntroduceVariableSettings settings) {
    GrVariableDeclaration varDecl = generateDeclaration(context, settings);

    if (context.getStringPart() != null) {
      final GrExpression ref = context.getStringPart().replaceLiteralWithConcatenation(DUMMY_NAME);
      return doProcessExpression(context, settings, varDecl, new PsiElement[]{ref}, ref);
    }
    else {
      final GrExpression expression = context.getExpression();
      assert expression != null;
      return doProcessExpression(context, settings, varDecl, context.getOccurrences(), expression);
    }
  }

  private GrVariable doProcessExpression(@NotNull GrIntroduceContext context,
                                         @NotNull GroovyIntroduceVariableSettings settings,
                                         @NotNull GrVariableDeclaration varDecl,
                                         PsiElement @NotNull [] elements,
                                         @NotNull GrExpression expression) {
    return new GrIntroduceLocalVariableProcessor(context, settings, elements, expression, true) {
      @Override
      protected void refreshPositionMarker(PsiElement e) {
        GrIntroduceVariableHandler.this.refreshPositionMarker(context.getEditor().getDocument().createRangeMarker(e.getTextRange()));
      }
    }.processExpression(varDecl);
  }

  void refreshPositionMarker(RangeMarker marker) {
    myPosition = marker;
  }

  private RangeMarker getPositionMarker() {
    return myPosition;
  }

  @Override
  protected @Nls(capitalization = Title) @NotNull String getRefactoringName() {
    return getRefactoringNameText();
  }

  @Override
  protected @NotNull String getHelpID() {
    return HelpID.INTRODUCE_VARIABLE;
  }

  @Override
  protected @NotNull GroovyIntroduceVariableDialog getDialog(@NotNull GrIntroduceContext context) {
    final GroovyVariableValidator validator = new GroovyVariableValidator(context);
    return new GroovyIntroduceVariableDialog(context, validator);
  }

  protected static @Nls(capitalization = Title) String getRefactoringNameText() {
    return GroovyRefactoringBundle.message("introduce.variable.title");
  }
}
