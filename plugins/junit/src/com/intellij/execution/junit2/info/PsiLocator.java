package com.intellij.execution.junit2.info;

import com.intellij.execution.Location;
import com.intellij.openapi.project.Project;

public interface PsiLocator {
  Location getLocation(Project project);
}
