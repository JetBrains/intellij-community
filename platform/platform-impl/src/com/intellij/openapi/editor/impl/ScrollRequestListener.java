// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import org.jetbrains.annotations.NotNull;


public interface ScrollRequestListener {
  void scrollRequested(@NotNull LogicalPosition targetPosition, @NotNull ScrollType scrollType);
}
