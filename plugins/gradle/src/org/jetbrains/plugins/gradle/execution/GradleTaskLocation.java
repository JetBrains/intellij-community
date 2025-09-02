// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution;

import com.intellij.execution.PsiLocation;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class GradleTaskLocation extends PsiLocation<PsiFile> {
  
  private final @NotNull List<String> myTasks;

  public GradleTaskLocation(@NotNull Project p, @NotNull PsiFile file, @NotNull List<String> tasks) {
    super(p, file);
    myTasks = tasks;
  }
  
  public @NotNull List<String> getTasks() {
    return myTasks;
  }
}