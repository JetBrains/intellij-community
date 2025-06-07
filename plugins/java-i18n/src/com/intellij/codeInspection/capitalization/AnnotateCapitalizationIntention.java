// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.capitalization;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.intention.AddAnnotationModCommandAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.java.i18n.JavaI18nBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public final class AnnotateCapitalizationIntention extends PsiBasedModCommandAction<PsiParameter> {

  public AnnotateCapitalizationIntention() {
    super(PsiParameter.class);
  }

  @Override
  protected boolean isFileAllowed(@NotNull PsiFile file) {
    return ApplicationManager.getApplication().isUnitTestMode() || !BaseIntentionAction.canModify(file);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiParameter parameter) {
    if (!(parameter.getDeclarationScope() instanceof PsiMethod) || !TypeUtils.isJavaLangString(parameter.getType())) return null;
    if (AnnotationUtil.findAnnotation(parameter, Nls.class.getName()) != null) return null;
    return Presentation.of(getFamilyName());
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaI18nBundle.message("intention.family.annotate.capitalization.type");
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiParameter parameter) {
    String nls = Nls.class.getName();
    ExternalAnnotationsManager manager = ExternalAnnotationsManager.getInstance(context.project());
    return ModCommand.chooseAction(
      getFamilyName(),
      ContainerUtil.map(List.of(Nls.Capitalization.Title, Nls.Capitalization.Sentence), capitalization -> {
        PsiAnnotation annotation = JavaPsiFacade.getInstance(context.project()).getElementFactory()
          .createAnnotationFromText("@" + nls + "(capitalization = " +
                                    nls + ".Capitalization." + capitalization.toString() + ")",
                                    parameter);
        return new AddAnnotationModCommandAction(nls,
                                                 parameter,
                                                 annotation.getParameterList().getAttributes(),
                                                 manager.chooseAnnotationsPlaceNoUi(parameter),
                                                 capitalization.name(), ArrayUtil.EMPTY_STRING_ARRAY); //NON-NLS
      }));
  }
}
