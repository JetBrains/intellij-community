// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

@VisibleForTesting
public final class UsePluginIdEqualsInspection extends UseEqualsInspectionBase {

  @Override
  protected @NotNull Class<?> getTargetClass() {
    return PluginId.class;
  }
}
