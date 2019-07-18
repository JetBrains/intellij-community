// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.scale;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides ScaleContext awareness of a UI object.
 *
 * @see ScaleContextSupport
 */
public interface ScaleContextAware {
  /**
   * @return the scale context
   */
  @NotNull
  UserScaleContext getScaleContext();

  /**
   * Updates the current context with the state of the provided context.
   * If {@code ctx} is null, then updates the current context via {@link ScaleContext#update()}
   * and returns the result.
   *
   * @param ctx the new scale context
   * @return whether any of the scale factors has been updated
   */
  boolean updateScaleContext(@Nullable UserScaleContext ctx);

  /**
   * @return the scale of the provided type from the context
   */
  double getScale(@NotNull ScaleType type);

  /**
   * @return the scale of the provided type from the context
   */
  double getScale(@NotNull DerivedScaleType type);

  /**
   * Sets the new scale in the context
   *
   * @return whether the provided scale updated the current value
   */
  boolean setScale(@NotNull Scale scale);
}
