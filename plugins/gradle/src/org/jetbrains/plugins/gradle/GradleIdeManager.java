// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Soroka
 */
public class GradleIdeManager {

  private static final @NotNull NotNullLazyValue<GradleIdeManager> myDefaultLazyValue = NotNullLazyValue.createValue(GradleIdeManager::new);

  public static GradleIdeManager getInstance() {
    GradleIdeManager ideManager = ServiceManager.getService(GradleIdeManager.class);
    return ideManager != null ? ideManager : myDefaultLazyValue.getValue();
  }

  public Object createTestConsoleProperties(Project project, Executor executor, RunConfiguration runConfiguration) {
    return null;
  }
}
