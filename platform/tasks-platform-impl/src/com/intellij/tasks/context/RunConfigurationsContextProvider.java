// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @NotNull
  public String getId() {
    return "runConfigurations";
  }

  @Override
  @NotNull
  public String getDescription() {
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
