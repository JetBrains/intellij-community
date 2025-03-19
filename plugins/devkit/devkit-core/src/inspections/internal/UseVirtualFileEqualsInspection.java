// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

@VisibleForTesting
@ApiStatus.Internal
public final class UseVirtualFileEqualsInspection extends UseEqualsInspectionBase {

  @Override
  protected @NotNull Class<?> getTargetClass() {
    return VirtualFile.class;
  }
}
