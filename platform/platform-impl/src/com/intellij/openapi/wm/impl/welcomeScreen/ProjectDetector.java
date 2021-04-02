// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.openapi.extensions.ExtensionPointName;

public interface ProjectDetector {
  ExtensionPointName<ProjectDetector> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.welcome.projectDetector");
  void detectProjects(Runnable onFinish);
}
