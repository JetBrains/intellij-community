// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;

import java.util.Optional;

/**
 * @author Vladislav.Soroka
 */
public class GradleIdeManager {

  public static GradleIdeManager getInstance() {
    return Optional.ofNullable(ServiceManager.getService(GradleIdeManager.class)).orElseGet(GradleIdeManager::new);
  }

  public Object createTestConsoleProperties(Project project, Executor executor, RunConfiguration runConfiguration) {
    return null;
  }
}
