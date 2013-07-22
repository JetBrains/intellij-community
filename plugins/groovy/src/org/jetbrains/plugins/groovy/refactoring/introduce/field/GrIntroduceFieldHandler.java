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
package org.jetbrains.plugins.groovy.refactoring.introduce.field;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser;
import com.intellij.refactoring.introduceField.IntroduceFieldHandler;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GrRefactoringError;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceDialog;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public class GrIntroduceFieldHandler extends GrIntroduceHandlerBase<GrIntroduceFieldSettings> {

  @NotNull
  @Override
  protected String getRefactoringName() {
    return IntroduceFieldHandler.REFACTORING_NAME;
  }

  @NotNull
  @Override
  protected String getHelpID() {
    return HelpID.INTRODUCE_FIELD;
  }

  @NotNull
  @Override
  protected PsiClass findScope(GrExpression expression, GrVariable variable, StringPartInfo partInfo) {
    PsiElement place = getCurrentPlace(expression, variable, partInfo);
    return ObjectUtils.assertNotNull(PsiUtil.getContextClass(place));
  }

  @Override
  protected void checkExpression(@NotNull GrExpression selectedExpr) {
    checkContainingClass(selectedExpr);
  }

  private static void checkContainingClass(PsiElement place) {
    final PsiClass containingClass = PsiUtil.getContextClass(place);
    if (containingClass == null) throw new GrRefactoringError(GroovyRefactoringBundle.message("cannot.introduce.field.in.script"));
    if (containingClass.isInterface()) {
      throw new GrRefactoringError(GroovyRefactoringBundle.message("cannot.introduce.field.in.interface"));
    }
    if (PsiUtil.skipParentheses(place, false) == null) {
      throw new GrRefactoringError(GroovyRefactoringBundle.message("expression.contains.errors"));
    }
  }

  @Override
  protected void checkVariable(@NotNull GrVariable variable) throws GrRefactoringError {
    checkContainingClass(variable);
  }

  @Override
  protected void checkStringLiteral(@NotNull StringPartInfo info) throws GrRefactoringError {
    checkContainingClass(info.getLiteral());
  }

  @Override
  protected void checkOccurrences(@NotNull PsiElement[] occurrences) {
    //nothing to do
  }

  @NotNull
  @Override
  protected GrIntroduceDialog<GrIntroduceFieldSettings> getDialog(@NotNull GrIntroduceContext context) {
    return new GrIntroduceFieldDialog(context, getApplicableInitPlaces(context));
  }

  @Override
  public GrVariable runRefactoring(@NotNull GrIntroduceContext context, @NotNull GrIntroduceFieldSettings settings) {
    return new GrIntroduceFieldProcessor(context, settings).run();
  }

  @Override
  protected GrInplaceFieldIntroducer getIntroducer(@NotNull final GrVariable var,
                                                   @NotNull GrIntroduceContext context,
                                                   @NotNull GrIntroduceFieldSettings settings,
                                                   @NotNull List<RangeMarker> occurrenceMarkers,
                                                   @Nullable RangeMarker varRangeMarker,
                                                   @Nullable RangeMarker expressionRangeMarker,
                                                   @Nullable RangeMarker stringPartRangeMarker) {
    if (varRangeMarker != null) {
      context.getEditor().getCaretModel().moveToOffset(var.getNameIdentifierGroovy().getTextRange().getStartOffset());
    }
    else if (expressionRangeMarker != null) {
      context.getEditor().getCaretModel().moveToOffset(expressionRangeMarker.getStartOffset());
    }
    else if (stringPartRangeMarker != null) {
      int offset = stringPartRangeMarker.getStartOffset();
      PsiElement at = var.getContainingFile().findElementAt(offset);
      GrExpression ref = PsiTreeUtil.getParentOfType(at, GrBinaryExpression.class).getRightOperand();
      context.getEditor().getCaretModel().moveToOffset(ref.getTextRange().getStartOffset());
    }
    GrExpression initializer =
      GroovyPsiElementFactory.getInstance(context.getProject()).createExpressionFromText(var.getInitializerGroovy().getText());
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        var.setInitializerGroovy(null);
      }
    });

    return new GrInplaceFieldIntroducer(var, context, occurrenceMarkers, settings.replaceAllOccurrences(), expressionRangeMarker,
                                        stringPartRangeMarker, initializer);
  }

  static EnumSet<GrIntroduceFieldSettings.Init> getApplicableInitPlaces(GrIntroduceContext context) {

    if (TestFrameworks.getInstance().isTestClass((PsiClass)context.getScope())) {
      return EnumSet.of(GrIntroduceFieldSettings.Init.CUR_METHOD,
                        GrIntroduceFieldSettings.Init.FIELD_DECLARATION,
                        GrIntroduceFieldSettings.Init.CONSTRUCTOR,
                        GrIntroduceFieldSettings.Init.SETUP_METHOD);
    }
    else {
      return EnumSet.of(GrIntroduceFieldSettings.Init.CUR_METHOD,
                        GrIntroduceFieldSettings.Init.FIELD_DECLARATION,
                        GrIntroduceFieldSettings.Init.CONSTRUCTOR);
    }

  }

  @Override
  protected GrIntroduceFieldSettings getSettingsForInplace(final GrIntroduceContext context, final OccurrencesChooser.ReplaceChoice choice) {
    return new GrIntroduceFieldSettings() {
      @Override
      public boolean declareFinal() {
        return false;
      }

      @Override
      public Init initializeIn() {
        return Init.FIELD_DECLARATION;
      }

      @Override
      public String getVisibilityModifier() {
        return PsiModifier.PRIVATE;
      }

      @Override
      public boolean isStatic() {
        boolean hasInstanceInScope = true;
        PsiClass clazz = (PsiClass)context.getScope();
        if (replaceAllOccurrences()) {
          for (PsiElement occurrence : context.getOccurrences()) {
            if (!PsiUtil.hasEnclosingInstanceInScope(clazz, occurrence, false)) {
              hasInstanceInScope = false;
              break;
            }
          }
        }
        else if (context.getExpression() != null) {
          hasInstanceInScope = PsiUtil.hasEnclosingInstanceInScope(clazz, context.getExpression(), false);
        }
        else if (context.getStringPart() != null) {
          hasInstanceInScope = PsiUtil.hasEnclosingInstanceInScope(clazz, context.getStringPart().getLiteral(), false);
        }

        return !hasInstanceInScope;
      }

      @Override
      public boolean removeLocalVar() {
        return context.getVar() != null;
      }

      @Nullable
      @Override
      public String getName() {
        return new GrFieldNameSuggester(context, new GroovyInplaceFieldValidator(context)).suggestNames().iterator().next();
      }

      @Override
      public boolean replaceAllOccurrences() {
        return context.getVar() != null || choice == OccurrencesChooser.ReplaceChoice.ALL;
      }

      @Nullable
      @Override
      public PsiType getSelectedType() {
        GrExpression expression = context.getExpression();
        GrVariable var = context.getVar();
        StringPartInfo stringPart = context.getStringPart();
        return var != null ? var.getDeclaredType() :
               expression != null ? expression.getType() :
               stringPart != null ? stringPart.getLiteral().getType() :
               null;
      }
    };
  }

  @NotNull
  @Override
  protected PsiElement[] findOccurrences(@NotNull GrExpression expression, @NotNull PsiElement scope) {
    if (scope instanceof GroovyScriptClass) {
      scope = scope.getContainingFile();
    }
    final PsiElement[] occurrences = super.findOccurrences(expression, scope);
    if (shouldBeStatic(expression, scope)) return occurrences;

    List<PsiElement> filtered = new ArrayList<PsiElement>();
    for (PsiElement occurrence : occurrences) {
      if (!shouldBeStatic(occurrence, scope)) {
        filtered.add(occurrence);
      }
    }
    return ContainerUtil.toArray(filtered, new PsiElement[filtered.size()]);
  }

  @Override
  protected boolean isInplace(GrIntroduceContext context) {
    return super.isInplace(context);
  }

  @Nullable
  static GrMember getContainer(@Nullable PsiElement place, @Nullable PsiElement scope) {
    while (place != null && place != scope) {
      place = place.getParent();
      if (place instanceof GrMember) return (GrMember)place;
    }
    return null;
  }

  static boolean shouldBeStatic(PsiElement expr, PsiElement clazz) {
    final GrMember method = getContainer(expr, clazz);
    if (method == null) return false;
    return method.hasModifierProperty(PsiModifier.STATIC);
  }
}
