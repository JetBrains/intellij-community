// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.accessibility;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides a minimal accessible information about the object.
 */
public interface SimpleAccessible {
  /**
   * Returns a human-readable string that designates the purpose of the object.
   */
  @Nls @NotNull String getAccessibleName();

  /**
   * Returns the tooltip text or {@code null} when the tooltip is not available.
   */
  @Nls(capitalization = Nls.Capitalization.Sentence) @Nullable String getAccessibleTooltipText();
}
