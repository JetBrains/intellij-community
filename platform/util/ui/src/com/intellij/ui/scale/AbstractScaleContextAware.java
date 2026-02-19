// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.scale;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// [tav] todo: [User]ScaleContext is thread-unsafe, should it be thread-safe?
public abstract class AbstractScaleContextAware<T extends UserScaleContext> implements ScaleContextAware {
  private final @NotNull T scaleContext;

  protected AbstractScaleContextAware(@NotNull T context) {
    scaleContext = context;
  }

  @Override
  public final @NotNull T getScaleContext() {
    return scaleContext;
  }

  @Override
  public final boolean updateScaleContext(@Nullable UserScaleContext ctx) {
    return scaleContext.update(ctx);
  }

  @Override
  public final double getScale(@NotNull ScaleType type) {
    return scaleContext.getScale(type);
  }

  @Override
  public final double getScale(@NotNull DerivedScaleType type) {
    return scaleContext.getScale(type);
  }

  @Override
  public final boolean setScale(@NotNull Scale scale) {
    return scaleContext.setScale(scale);
  }
}
