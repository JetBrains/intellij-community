// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util;

import org.gradle.api.file.RegularFile;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.tooling.util.ReflectionUtil;

import java.io.File;

public final class GradleArchiveTaskUtil {

  private static final GradleVersion gradleBaseVersion = GradleVersion.current().getBaseVersion();
  private static final boolean is51OrBetter = gradleBaseVersion.compareTo(GradleVersion.version("5.1")) >= 0;

  public static @Nullable File getArchiveFile(@NotNull AbstractArchiveTask task) {
    return is51OrBetter ?
           ReflectionUtil.reflectiveGetProperty(task, "getArchiveFile", RegularFile.class).getAsFile() :
           ReflectionUtil.reflectiveCall(task, "getArchivePath", File.class);
  }

  public static @Nullable String getArchiveFileName(@NotNull AbstractArchiveTask task) {
    return is51OrBetter ?
           ReflectionUtil.reflectiveGetProperty(task, "getArchiveFileName", String.class) :
           ReflectionUtil.reflectiveCall(task, "getArchiveName", String.class);
  }
}
