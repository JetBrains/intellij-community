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
package org.jetbrains.plugins.groovy.refactoring.introduce.variable;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.*;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.formatter.GrControlStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.refactoring.GrRefactoringError;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContextImpl;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;

/**
 * Created by Max Medvedev on 10/29/13
 */
public class GrIntroduceVariableHandler extends GrIntroduceHandlerBase<GroovyIntroduceVariableSettings, GrControlFlowOwner> {
  public static final String DUMMY_NAME = "________________xxx_________________";
  protected static final String REFACTORING_NAME = GroovyRefactoringBundle.message("introduce.variable.title");
  private RangeMarker myPosition = null;

  @NotNull
  @Override
  protected GrControlFlowOwner[] findPossibleScopes(GrExpression selectedExpr,
                                                    GrVariable variable,
                                                    StringPartInfo stringPartInfo,
                                                    Editor editor) {
    // Get container element
    final GrControlFlowOwner scope = ControlFlowUtils.findControlFlowOwner(stringPartInfo != null ? stringPartInfo.getLiteral() : selectedExpr);
    if (scope == null) {
      throw new GrRefactoringError(
        GroovyRefactoringBundle.message("refactoring.is.not.supported.in.the.current.context", REFACTORING_NAME));
    }
    if (!GroovyRefactoringUtil.isAppropriateContainerForIntroduceVariable(scope)) {
      throw new GrRefactoringError(
        GroovyRefactoringBundle.message("refactoring.is.not.supported.in.the.current.context", REFACTORING_NAME));
    }
    return new GrControlFlowOwner[]{scope};
  }

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
  protected void checkOccurrences(@NotNull PsiElement[] occurrences) {
    //nothing to do
  }

  private static boolean checkInFieldInitializer(@NotNull GrExpression expr) {
    PsiElement parent = expr.getParent();
    if (parent instanceof GrClosableBlock) {
      return false;
    }
    if (parent instanceof GrField && expr == ((GrField)parent).getInitializerGroovy()) {
      return true;
    }
    if (parent instanceof GrExpression) {
      return checkInFieldInitializer(((GrExpression)parent));
    }
    return false;
  }

  /**
   * Inserts new variable declarations and replaces occurrences
   */
  public GrVariable runRefactoring(@NotNull final GrIntroduceContext context, @NotNull final GroovyIntroduceVariableSettings settings) {
    // Generating variable declaration

    GrVariable insertedVar = processExpression(context, settings);

    if (context.getEditor() != null && getPositionMarker() != null) {
      context.getEditor().getCaretModel().moveToOffset(getPositionMarker().getEndOffset());
      context.getEditor().getSelectionModel().removeSelection();
    }
    return insertedVar;
  }

  @Override
  protected GrInplaceVariableIntroducer getIntroducer(@NotNull GrIntroduceContext context, OccurrencesChooser.ReplaceChoice choice) {

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
        if (processUsages) {
          return processExpression(context, settings);
        }
        else {
          return addVariable(context, settings);
        }
      }
    };
  }

  private static void extractStringPart(final Ref<GrIntroduceContext> ref) {
    CommandProcessor.getInstance().executeCommand(ref.get().getProject(), new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            GrIntroduceContext context = ref.get();

            GrExpression expression = cutLiteral(context.getStringPart(), context.getProject());

            ref.set(new GrIntroduceContextImpl(context.getProject(), context.getEditor(), expression, null, null, new PsiElement[]{expression}, context.getScope()));
          }
        });
      }
    }, REFACTORING_NAME, REFACTORING_NAME);
  }

  private static void addBraces(@NotNull final GrStatement anchor, @NotNull final Ref<GrIntroduceContext> contextRef) {
    CommandProcessor.getInstance().executeCommand(contextRef.get().getProject(), new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            GrIntroduceContext context = contextRef.get();
            SmartPointerManager pointManager = SmartPointerManager.getInstance(context.getProject());
            SmartPsiElementPointer<GrExpression> expressionRef = context.getExpression() != null ? pointManager.createSmartPsiElementPointer(context.getExpression()) : null;
            SmartPsiElementPointer<GrVariable> varRef = context.getVar() != null ? pointManager.createSmartPsiElementPointer(context.getVar()) : null;

            SmartPsiElementPointer[] occurrencesRefs = new SmartPsiElementPointer[context.getOccurrences().length];
            PsiElement[] occurrences = context.getOccurrences();
            for (int i = 0; i < occurrences.length; i++) {
              occurrencesRefs[i] = pointManager.createSmartPsiElementPointer(occurrences[i]);
            }


            PsiFile file = anchor.getContainingFile();
            SmartPsiFileRange anchorPointer = pointManager.createSmartPsiFileRangePointer(file, anchor.getTextRange());

            Document document = context.getEditor().getDocument();
            CharSequence sequence = document.getCharsSequence();

            TextRange range = anchor.getTextRange();

            int end = range.getEndOffset();
            document.insertString(end, "\n}");

            int start = range.getStartOffset();
            while (start > 0 && Character.isWhitespace(sequence.charAt(start - 1))) {
              start--;
            }
            document.insertString(start, "{");

            PsiDocumentManager.getInstance(context.getProject()).commitDocument(document);

            Segment anchorSegment = anchorPointer.getRange();
            PsiElement restoredAnchor = GroovyRefactoringUtil.findElementInRange(file, anchorSegment.getStartOffset(), anchorSegment.getEndOffset(), PsiElement.class);
            GrCodeBlock block = (GrCodeBlock)restoredAnchor.getParent();
            CodeStyleManager.getInstance(context.getProject()).reformat(block.getRBrace());
            CodeStyleManager.getInstance(context.getProject()).reformat(block.getLBrace());

            for (int i = 0; i < occurrencesRefs.length; i++) {
              occurrences[i] = occurrencesRefs[i].getElement();
            }

            contextRef.set(new GrIntroduceContextImpl(context.getProject(), context.getEditor(),
                                                      expressionRef != null ? expressionRef.getElement() : null,
                                                      varRef != null ? varRef.getElement() : null,
                                                      null, occurrences, context.getScope()));
          }
        });
      }
    }, REFACTORING_NAME, REFACTORING_NAME);
  }

  private static GrVariable addVariable(@NotNull GrIntroduceContext context, @NotNull GroovyIntroduceVariableSettings settings) {
    GrStatement anchor = findAnchor(context, settings.replaceAllOccurrences());
    PsiElement parent = anchor.getParent();
    assert parent instanceof GrStatementOwner;
    GrStatement declaration = ((GrStatementOwner)parent).addStatementBefore(generateDeclaration(context, settings), anchor);

    return ((GrVariableDeclaration)declaration).getVariables()[0];
  }

  @NotNull
  private static GrStatement findAnchor(@NotNull final GrIntroduceContext context, final boolean replaceAll) {
    return ApplicationManager.getApplication().runReadAction(new Computable<GrStatement>() {
      @Override
      public GrStatement compute() {
        PsiElement[] occurrences = replaceAll ? context.getOccurrences() : new GrExpression[]{context.getExpression()};
        return GrIntroduceLocalVariableProcessor.getAnchor(occurrences, context.getScope());
      }
    });
  }

  @Override
  protected void showScopeChooser(GrControlFlowOwner[] scopes, Pass<GrControlFlowOwner> callback, Editor editor) {
    //todo do nothing right now
  }

  @NotNull
  private static GrVariableDeclaration generateDeclaration(@NotNull GrIntroduceContext context,
                                                           @NotNull GroovyIntroduceVariableSettings settings) {
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(context.getProject());
    final String[] modifiers = settings.isDeclareFinal() ? new String[]{PsiModifier.FINAL} : null;

    final GrVariableDeclaration declaration =
      factory.createVariableDeclaration(modifiers, "foo", settings.getSelectedType(), settings.getName());

    generateInitializer(context, declaration.getVariables()[0]);
    return declaration;
  }

  @NotNull
  private GrVariable processExpression(@NotNull GrIntroduceContext context,
                                       @NotNull GroovyIntroduceVariableSettings settings) {
    GrVariableDeclaration varDecl = generateDeclaration(context, settings);

    if (context.getStringPart() != null) {
      final GrExpression ref = processLiteral(DUMMY_NAME, context.getStringPart(), context.getProject());
      return doProcessExpression(context, settings, varDecl, new PsiElement[]{ref}, ref, true);
    }
    else {
      final GrExpression expression = context.getExpression();
      assert expression != null;
      return doProcessExpression(context, settings, varDecl, context.getOccurrences(), expression, true);
    }
  }

  private GrVariable doProcessExpression(@NotNull final GrIntroduceContext context,
                                         @NotNull GroovyIntroduceVariableSettings settings,
                                         @NotNull GrVariableDeclaration varDecl,
                                         @NotNull PsiElement[] elements,
                                         @NotNull GrExpression expression, boolean processUsages) {
    return new GrIntroduceLocalVariableProcessor(context, settings, elements, expression, processUsages) {
      @Override
      protected void refreshPositionMarker(PsiElement e) {
        GrIntroduceVariableHandler.this.refreshPositionMarker(context.getEditor().getDocument().createRangeMarker(e.getTextRange()));
      }
    }.processExpression(varDecl);
  }

  @NotNull
  private static GrExpression generateInitializer(@NotNull GrIntroduceContext context,
                                                  @NotNull GrVariable variable) {
    final GrExpression initializer = context.getStringPart() != null
                                     ? GrIntroduceHandlerBase.generateExpressionFromStringPart(context.getStringPart(), context.getProject())
                                     : context.getExpression();
    final GrExpression dummyInitializer = variable.getInitializerGroovy();
    assert dummyInitializer != null;
    return dummyInitializer.replaceWithExpression(initializer, true);
  }

  void refreshPositionMarker(RangeMarker marker) {
    myPosition = marker;
  }

  private RangeMarker getPositionMarker() {
    return myPosition;
  }

  @NotNull
  @Override
  protected String getRefactoringName() {
    return REFACTORING_NAME;
  }

  @NotNull
  @Override
  protected String getHelpID() {
    return HelpID.INTRODUCE_VARIABLE;
  }

  @NotNull
  protected GroovyIntroduceVariableDialog getDialog(@NotNull GrIntroduceContext context) {
    final GroovyVariableValidator validator = new GroovyVariableValidator(context);
    return new GroovyIntroduceVariableDialog(context, validator);
  }
}
