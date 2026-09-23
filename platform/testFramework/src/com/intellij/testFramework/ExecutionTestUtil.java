// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

@TestOnly
public final class ExecutionTestUtil {
  public static <T extends RunConfiguration> @Nullable T getRunConfiguration(@NotNull PsiElement element, @NotNull RunConfigurationProducer<T> producer) {
    var dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, element.getProject())
      .add(PlatformCoreDataKeys.MODULE, ModuleUtilCore.findModuleForPsiElement(element))
      .add(Location.DATA_KEY, PsiLocation.fromPsiElement(element))
      .build();

    var cc = ConfigurationContext.getFromContext(dataContext, ActionPlaces.UNKNOWN);

    var configuration = producer.createConfigurationFromContext(cc);
    //noinspection unchecked
    return configuration != null ? (T)configuration.getConfiguration() : null;
  }
}
