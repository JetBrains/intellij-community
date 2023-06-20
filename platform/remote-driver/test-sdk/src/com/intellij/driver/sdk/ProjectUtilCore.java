package com.intellij.driver.sdk;

import com.intellij.driver.client.Remote;
import org.jetbrains.annotations.NotNull;

@Remote("com.intellij.ide.impl.ProjectUtilCore")
public interface ProjectUtilCore {
  @NotNull Project @NotNull [] getOpenProjects();
}
