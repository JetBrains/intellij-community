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

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.refactoring.GrRefactoringError;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;

import java.util.List;

/**
 * @author ilyas
 */
public class GrIntroduceVariableHandler extends GrIntroduceHandlerBase<GroovyIntroduceVariableSettings> {
  public static final String DUMMY_NAME = "________________xxx_________________";
  protected static final String REFACTORING_NAME = GroovyRefactoringBundle.message("introduce.variable.title");
  private RangeMarker myPosition = null;

  @NotNull
  @Override
  protected PsiElement findScope(GrExpression selectedExpr, GrVariable variable, StringPartInfo stringPartInfo) {
    // Get container element
    final PsiElement scope = stringPartInfo != null
                             ? GroovyRefactoringUtil.getEnclosingContainer(stringPartInfo.getLiteral())
                             : GroovyRefactoringUtil.getEnclosingContainer(selectedExpr);
    if (scope == null || !(scope instanceof GroovyPsiElement)) {
      throw new GrRefactoringError(
        GroovyRefactoringBundle.message("refactoring.is.not.supported.in.the.current.context", REFACTORING_NAME));
    }
    if (!GroovyRefactoringUtil.isAppropriateContainerForIntroduceVariable(scope)) {
      throw new GrRefactoringError(
        GroovyRefactoringBundle.message("refactoring.is.not.supported.in.the.current.context", REFACTORING_NAME));
    }
    return scope;
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

    final GrVariableDeclaration varDecl = generateDeclaration(context, settings);
    GrVariable insertedVar = processExpression(context, settings, varDecl);

    if (context.getEditor() != null && getPositionMarker() != null) {
      context.getEditor().getCaretModel().moveToOffset(getPositionMarker().getEndOffset());
      context.getEditor().getSelectionModel().removeSelection();
    }
    return insertedVar;
  }

  @Override
  protected GrInplaceVariableIntroducer getIntroducer(@NotNull GrVariable var,
                                                      @NotNull GrIntroduceContext context,
                                                      @NotNull GroovyIntroduceVariableSettings settings,
                                                      @NotNull List<RangeMarker> occurrenceMarkers,
                                                      RangeMarker varRangeMarker, RangeMarker expressionRangeMarker,
                                                      RangeMarker stringPartRangeMarker) {
    context.getEditor().getCaretModel().moveToOffset(var.getTextOffset());
    return new GrInplaceVariableIntroducer(var, context.getEditor(), context.getProject(), REFACTORING_NAME, occurrenceMarkers, var);
  }

  @Override
  protected GroovyIntroduceVariableSettings getSettingsForInplace(final GrIntroduceContext context, final OccurrencesChooser.ReplaceChoice choice) {
    return new GroovyIntroduceVariableSettings() {
      @Override
      public boolean isDeclareFinal() {
        return false;
      }

      @Nullable
      @Override
      public String getName() {
        return new GrVariableNameSuggester(context, new GroovyVariableValidator(context)).suggestNames().iterator().next();
      }

      @Override
      public boolean replaceAllOccurrences() {
        return choice == OccurrencesChooser.ReplaceChoice.ALL;
      }

      @Nullable
      @Override
      public PsiType getSelectedType() {
        GrExpression expression = context.getExpression();
        StringPartInfo stringPart = context.getStringPart();
        GrVariable var = context.getVar();
        return expression != null ? expression.getType() :
               var != null ? var.getType() :
               stringPart != null ? stringPart.getLiteral().getType() :
               null;
      }
    };
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
                                       @NotNull GroovyIntroduceVariableSettings settings,
                                       @NotNull GrVariableDeclaration varDecl) {
    if (context.getStringPart() != null) {
      final GrExpression ref = processLiteral(DUMMY_NAME, context.getStringPart(), context.getProject());
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
                                         @NotNull PsiElement[] elements,
                                         @NotNull GrExpression expression) {
    return new GrIntroduceLocalVariableProcessor(context, settings, elements, expression, this).processExpression(varDecl);
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
