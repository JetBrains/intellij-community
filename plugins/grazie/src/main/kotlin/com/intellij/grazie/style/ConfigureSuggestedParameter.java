package com.intellij.grazie.style;

import ai.grazie.gec.model.problem.ActionSuggestion;
import ai.grazie.rules.tree.Parameter;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.grazie.ide.ui.configurable.StyleConfigurable;
import com.intellij.grazie.jlanguage.Lang;
import com.intellij.grazie.utils.TextStyleDomain;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

public record ConfigureSuggestedParameter(ActionSuggestion.ChangeParameter parameter,
                                          TextStyleDomain domain, Lang lang, @NlsSafe String text)
  implements LocalQuickFix, DumbAware {

  public ConfigureSuggestedParameter {
    assert !parameter.getParameterId().endsWith(Parameter.LANGUAGE_VARIANT);
  }

  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return text;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    StyleConfigurable.focusSetting(parameter, domain, lang, project);
  }
}
