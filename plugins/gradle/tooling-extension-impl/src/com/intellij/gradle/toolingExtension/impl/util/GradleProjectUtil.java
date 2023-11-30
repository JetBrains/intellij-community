// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util;

import org.gradle.api.Project;
import org.gradle.tooling.model.ProjectIdentifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.InternalProjectIdentifier;

public final class GradleProjectUtil {

  public static @NotNull ProjectIdentifier getProjectIdentifier(@NotNull Project project) {
    return new InternalProjectIdentifier(project.getRootDir(), project.getPath());
  }
}
