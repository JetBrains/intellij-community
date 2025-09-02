// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util;

import com.intellij.gradle.toolingExtension.util.GradleReflectionUtil;
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil;
import org.gradle.api.Task;
import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.metaobject.AbstractDynamicObject;
import org.gradle.internal.metaobject.DynamicObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public final class GradleTaskUtil {

  private static final boolean is49OrBetter = GradleVersionUtil.isCurrentGradleAtLeast("4.9");
  private static final boolean is51OrBetter = GradleVersionUtil.isCurrentGradleAtLeast("5.1");

  private static @Nullable Object getProperty(@NotNull Task task, @NotNull String propertyName) {
    ExtensionContainer extensions = task.getExtensions();
    ExtraPropertiesExtension propertiesExtension = extensions.getExtraProperties();
    if (!propertiesExtension.has(propertyName)) {
      return null;
    }
    return propertiesExtension.get(propertyName);
  }

  public static boolean getBooleanProperty(@NotNull Task task, @NotNull String propertyName, boolean defaultValue) {
    Object value = getProperty(task, propertyName);
    if (value == null) {
      return defaultValue;
    }
    return Boolean.parseBoolean(value.toString());
  }

  public static @Nullable Class<?> getTaskIdentityType(@NotNull TaskInternal task) {
    if (is49OrBetter) {
      return task.getTaskIdentity().type;
    }
    if (task instanceof DynamicObjectAware) {
      DynamicObject dynamicObject = ((DynamicObjectAware)task).getAsDynamicObject();
      if (dynamicObject instanceof AbstractDynamicObject) {
        return ((AbstractDynamicObject)dynamicObject).getPublicType();
      }
    }
    return null;
  }

  public static @NotNull File getTaskArchiveFile(@NotNull AbstractArchiveTask task) {
    if (is51OrBetter) {
      return task.getArchiveFile().get().getAsFile();
    }
    return GradleReflectionUtil.getValue(task, "getArchivePath", File.class);
  }

  public static @NotNull String getTaskArchiveFileName(@NotNull AbstractArchiveTask task) {
    if (is51OrBetter) {
      return task.getArchiveFileName().get();
    }
    return GradleReflectionUtil.getValue(task, "getArchiveName", String.class);
  }

  public static void setTaskTestForkEvery(@NotNull Test task, long forkEvery) {
    if (GradleVersionUtil.isCurrentGradleAtLeast("8.1")) {
      task.setForkEvery(forkEvery);
      return;
    }
    GradleReflectionUtil.setValue(task, "setForkEvery", Long.class, forkEvery);
  }
}
