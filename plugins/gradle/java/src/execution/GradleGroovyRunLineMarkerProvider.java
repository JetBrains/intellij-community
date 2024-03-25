// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution;

import com.intellij.execution.lineMarker.ExecutorAction;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.util.text.StringUtil.join;
import static com.intellij.openapi.util.text.Strings.isNotEmpty;
import static com.intellij.util.containers.ContainerUtil.mapNotNull;
import static org.jetbrains.plugins.gradle.execution.GradleGroovyRunnerUtil.getTaskNameIfContains;
import static org.jetbrains.plugins.gradle.execution.GradleRunnerUtil.isFromGroovyGradleScript;

/**
 * @author Vladislav.Soroka
 */
public final class GradleGroovyRunLineMarkerProvider extends RunLineMarkerContributor {
  @Nullable
  @Override
  public Info getInfo(@NotNull final PsiElement element) {
    if (!isFromGroovyGradleScript(element)) return null;
    String taskName = getTaskNameIfContains(element);
    if (isNotEmpty(taskName)) {
      AnAction[] actions = ExecutorAction.getActions();
      AnActionEvent event = createActionEvent(element);
      return new Info(AllIcons.RunConfigurations.TestState.Run, actions,
                      e -> join(mapNotNull(actions, action -> getText(action, event)), "\n"));
    }
    return null;
  }
}