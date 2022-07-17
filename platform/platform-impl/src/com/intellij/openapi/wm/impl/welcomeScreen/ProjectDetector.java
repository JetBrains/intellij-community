// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.ProjectGroup;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

public abstract class ProjectDetector {
  public static final ExtensionPointName<ProjectDetector> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.welcome.projectDetector");

  public abstract void detectProjects(Consumer<? super List<String>> onFinish);

  public void logRecentProjectOpened(@Nullable ProjectGroup projectGroup) {}

  public static void runDetectors(Consumer<? super List<String>> onFinish) {
    ProjectDetector @NotNull [] extensions = EXTENSION_POINT_NAME.getExtensions();
    for (ProjectDetector detector : extensions) {
      detector.detectProjects(onFinish);
    }
  }
}
