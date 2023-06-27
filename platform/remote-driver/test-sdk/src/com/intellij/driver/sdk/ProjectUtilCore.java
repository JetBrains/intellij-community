package com.intellij.driver.sdk;

import com.intellij.driver.client.Remote;
import com.intellij.driver.client.Timed;
import org.jetbrains.annotations.NotNull;

@Remote("com.intellij.ide.impl.ProjectUtilCore")
public interface ProjectUtilCore {
  @Timed("super-quick-projects-get")
  @NotNull Project @NotNull [] getOpenProjects();
}
