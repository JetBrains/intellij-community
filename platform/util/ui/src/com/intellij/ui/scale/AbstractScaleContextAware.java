// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.scale;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// [tav] todo: [User]ScaleContext is thread-unsafe, should it be thread-safe?
abstract class AbstractScaleContextAware<T extends UserScaleContext> implements ScaleContextAware {
  @NotNull
  private final T myScaleContext;

  AbstractScaleContextAware(@NotNull T ctx) {
    myScaleContext = ctx;
  }

  @NotNull
  @Override
  public T getScaleContext() {
    return myScaleContext;
  }

  @Override
  public boolean updateScaleContext(@Nullable UserScaleContext ctx) {
    return myScaleContext.update(ctx);
  }

  @Override
  public double getScale(@NotNull ScaleType type) {
    return getScaleContext().getScale(type);
  }

  @Override
  public double getScale(@NotNull DerivedScaleType type) {
    return getScaleContext().getScale(type);
  }

  @Override
  public boolean setScale(@NotNull Scale scale) {
    return getScaleContext().setScale(scale);
  }
}
