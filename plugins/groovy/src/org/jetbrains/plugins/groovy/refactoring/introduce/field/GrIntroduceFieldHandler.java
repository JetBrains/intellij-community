// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.introduce.field;

import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser;
import com.intellij.refactoring.introduceField.IntroduceFieldHandler;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GrRefactoringError;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.introduce.*;

import java.util.ArrayList;
import java.util.List;

import static org.jetbrains.annotations.Nls.Capitalization.Title;

/**
 * @author Maxim.Medvedev
 */
public class GrIntroduceFieldHandler extends GrIntroduceFieldHandlerBase<GrIntroduceFieldSettings> {

  @Override
  protected @Nls(capitalization = Title) @NotNull String getRefactoringName() {
    return IntroduceFieldHandler.getRefactoringNameText();
  }

  @NotNull
  @Override
  protected String getHelpID() {
    return HelpID.INTRODUCE_FIELD;
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
  protected void checkOccurrences(PsiElement @NotNull [] occurrences) {
    //nothing to do
  }

  @NotNull
  @Override
  protected GrIntroduceDialog<GrIntroduceFieldSettings> getDialog(@NotNull GrIntroduceContext context) {
    return new GrIntroduceFieldDialog(context);
  }

  @Override
  public GrVariable runRefactoring(@NotNull GrIntroduceContext context, @NotNull GrIntroduceFieldSettings settings) {
    return new GrIntroduceFieldProcessor(context, settings).run();
  }


  @Override
  protected GrAbstractInplaceIntroducer<GrIntroduceFieldSettings> getIntroducer(@NotNull GrIntroduceContext context,
                                                                                @NotNull OccurrencesChooser.ReplaceChoice choice) {

    final Ref<GrIntroduceContext> contextRef = Ref.create(context);

    if (context.getStringPart() != null) {
      extractStringPart(contextRef);
    }

    return new GrInplaceFieldIntroducer(contextRef.get(), choice);
  }

  @Override
  protected PsiElement @NotNull [] findOccurrences(@NotNull GrExpression expression, @NotNull PsiElement scope) {
    final PsiElement[] occurrences = super.findOccurrences(expression, scope);
    if (shouldBeStatic(expression, scope)) return occurrences;

    List<PsiElement> filtered = new ArrayList<>();
    for (PsiElement occurrence : occurrences) {
      if (!shouldBeStatic(occurrence, scope)) {
        filtered.add(occurrence);
      }
    }
    return filtered.toArray(PsiElement.EMPTY_ARRAY);
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
