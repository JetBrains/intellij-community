package com.intellij.driver.sdk;

import com.intellij.driver.client.Remote;
import org.jetbrains.annotations.NotNull;

@Remote("com.intellij.openapi.roots.ProjectRootManager")
public interface ProjectRootManager {
  VirtualFile @NotNull [] getContentRoots();
}
