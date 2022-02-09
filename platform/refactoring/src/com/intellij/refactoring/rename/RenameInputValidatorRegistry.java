// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.intellij.openapi.util.Pair.pair;

public final class RenameInputValidatorRegistry {
  private RenameInputValidatorRegistry() { }

  @Nullable
  public static Condition<String> getInputValidator(PsiElement element) {
    List<Pair<RenameInputValidator, ProcessingContext>> validators = new ArrayList<>();
    for (RenameInputValidator validator : RenameInputValidator.EP_NAME.getExtensionList()) {
      ProcessingContext context = new ProcessingContext();
      if (validator.getPattern().accepts(element, context)) {
        validators.add(pair(validator, context));
      }
    }

    return validators.isEmpty() ? null : newName -> ContainerUtil.and(validators, p -> p.first.isInputValid(newName, element, p.second));
  }

  @Nullable
  public static Function<String, @DialogMessage String> getInputErrorValidator(PsiElement element) {
    List<RenameInputValidatorEx> validators = new ArrayList<>();
    for (RenameInputValidator validator : RenameInputValidator.EP_NAME.getExtensionList()) {
      if (validator instanceof RenameInputValidatorEx && validator.getPattern().accepts(element, new ProcessingContext())) {
        validators.add((RenameInputValidatorEx)validator);
      }
    }

    if (validators.isEmpty()) return null;
    Project project = element.getProject();
    return newName -> validators.stream().map(v -> v.getErrorMessage(newName, project)).filter(Objects::nonNull).findFirst().orElse(null);
  }
}