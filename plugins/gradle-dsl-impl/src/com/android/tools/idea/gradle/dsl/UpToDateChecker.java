// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.gradle.dsl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
public interface UpToDateChecker {
  ExtensionPointName<UpToDateChecker>
    EXTENSION_POINT_NAME = ExtensionPointName.create("org.jetbrains.idea.gradle.dsl.upToDateChecker");

  boolean checkUpToDate(Project project);

  void setUpToDate(Project project);
}
