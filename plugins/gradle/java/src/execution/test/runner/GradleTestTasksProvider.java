// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vladislav.Soroka
 */
public interface GradleTestTasksProvider {
  ExtensionPointName<GradleTestTasksProvider> EP_NAME = ExtensionPointName.create("org.jetbrains.plugins.gradle.testTasksProvider");

  @NotNull
  List<String> getTasks(@NotNull Module module);

  default @NotNull List<String> getTasks(@NotNull Module module, @NotNull VirtualFile source) {
    return getTasks(module);
  }
}
