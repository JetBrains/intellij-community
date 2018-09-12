// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.accessibility;

import org.jetbrains.annotations.NotNull;

/**
 * Provides a minimal accessible human-readable description of an object.
 */
public interface DescAccessible {
  @NotNull
  String getAccessibleDesc();
}
