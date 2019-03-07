// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.context;

import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.openapi.project.Project;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class RunConfigurationsContextProvider extends WorkingContextProvider {
  @NotNull private final Project myProject;

  public RunConfigurationsContextProvider(@NotNull Project project) {
    myProject = project;
  }

  @Override
  @NotNull
  public String getId() {
    return "runConfigurations";
  }

  @Override
  @NotNull
  public String getDescription() {
    return "Run Configurations";
  }

  @Override
  public void saveContext(@NotNull Element toElement) {
    RunManagerImpl.getInstanceImpl(myProject).writeContext(toElement);
  }

  @Override
  public void loadContext(@NotNull Element fromElement) {
    RunManagerImpl.getInstanceImpl(myProject).readContext(fromElement);
  }
}
