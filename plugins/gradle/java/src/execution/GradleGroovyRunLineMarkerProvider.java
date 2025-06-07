// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution;

import com.intellij.execution.lineMarker.ExecutorAction;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.util.text.Strings.isNotEmpty;
import static org.jetbrains.plugins.gradle.execution.GradleGroovyRunnerUtil.getTaskNameIfContains;
import static org.jetbrains.plugins.gradle.execution.GradleRunnerUtil.isFromGroovyGradleScript;

/**
 * @author Vladislav.Soroka
 */
public final class GradleGroovyRunLineMarkerProvider extends RunLineMarkerContributor {
  @Override
  public @Nullable Info getInfo(final @NotNull PsiElement element) {
    if (!isFromGroovyGradleScript(element)) return null;
    String taskName = getTaskNameIfContains(element);
    if (!isNotEmpty(taskName)) return null;

    AnAction[] actions = ExecutorAction.getActions();
    return new Info(AllIcons.RunConfigurations.TestState.Run, actions);
  }
}