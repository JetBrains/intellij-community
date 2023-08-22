package com.intellij.driver.sdk;

import com.intellij.driver.client.ProjectRef;
import com.intellij.driver.client.Remote;

@Remote("com.intellij.openapi.project.Project")
public interface Project extends ProjectRef {
  boolean isOpen();

  boolean isInitialized();

  String getName();

  String getPresentableUrl();

  VirtualFile getProjectFile();
}
