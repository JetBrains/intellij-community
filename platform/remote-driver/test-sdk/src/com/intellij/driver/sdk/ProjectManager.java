package com.intellij.driver.sdk;

import com.intellij.driver.client.Remote;
import org.jetbrains.annotations.NotNull;

@Remote("com.intellij.openapi.project.ProjectManager")
public interface ProjectManager {
  Project @NotNull [] getOpenProjects();
}
