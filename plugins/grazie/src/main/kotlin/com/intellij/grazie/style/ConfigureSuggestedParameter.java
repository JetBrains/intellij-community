package com.intellij.grazie.style;

import ai.grazie.rules.tree.Parameter;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.grazie.ide.ui.configurable.StyleConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public record ConfigureSuggestedParameter(Parameter parameter, String text) implements LocalQuickFix {

  public ConfigureSuggestedParameter {
    assert !parameter.id().equals(Parameter.LANGUAGE_VARIANT);
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
    StyleConfigurable.focusSetting(parameter, project).navigate(true);
    //todo FUS?
  }
}
