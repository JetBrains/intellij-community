// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.context;

import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.tasks.TaskBundle;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
final class RunConfigurationsContextProvider extends WorkingContextProvider {
  @Override
  public @NotNull String getId() {
    return "runConfigurations";
  }

  @Override
  public @NotNull String getDescription() {
    return TaskBundle.message("run.configurations");
  }

  @Override
  public void saveContext(@NotNull Project project, @NotNull Element toElement) {
    RunManagerImpl.getInstanceImpl(project).writeContext(toElement);
  }

  @Override
  public void loadContext(@NotNull Project project, @NotNull Element fromElement) {
    RunManagerImpl.getInstanceImpl(project).readContext(fromElement);
  }
}
