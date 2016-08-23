/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser;
import com.intellij.refactoring.introduceField.IntroduceFieldHandler;
import com.intellij.util.containers.ContainerUtil;
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

/**
 * @author Maxim.Medvedev
 */
public class GrIntroduceFieldHandler extends GrIntroduceFieldHandlerBase<GrIntroduceFieldSettings> {

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
    return new GrIntroduceFieldDialog(context);
  }

  @Override
  public GrVariable runRefactoring(@NotNull GrIntroduceContext context, @NotNull GrIntroduceFieldSettings settings) {
    return new GrIntroduceFieldProcessor(context, settings).run();
  }


  @Override
  protected GrAbstractInplaceIntroducer<GrIntroduceFieldSettings> getIntroducer(@NotNull GrIntroduceContext context,
                                                                                OccurrencesChooser.ReplaceChoice choice) {

    final Ref<GrIntroduceContext> contextRef = Ref.create(context);

    if (context.getStringPart() != null) {
      extractStringPart(contextRef);
    }

    return new GrInplaceFieldIntroducer(contextRef.get(), choice);
  }

  @NotNull
  @Override
  protected PsiElement[] findOccurrences(@NotNull GrExpression expression, @NotNull PsiElement scope) {
    final PsiElement[] occurrences = super.findOccurrences(expression, scope);
    if (shouldBeStatic(expression, scope)) return occurrences;

    List<PsiElement> filtered = new ArrayList<>();
    for (PsiElement occurrence : occurrences) {
      if (!shouldBeStatic(occurrence, scope)) {
        filtered.add(occurrence);
      }
    }
    return ContainerUtil.toArray(filtered, new PsiElement[filtered.size()]);
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
