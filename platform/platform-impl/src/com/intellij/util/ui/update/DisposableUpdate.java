// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.update;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class DisposableUpdate extends Update {
  private final Disposable myParentDisposable;

  public DisposableUpdate(@NotNull Disposable parentDisposable, @NonNls @NotNull Object identity) {
    super(identity);
    myParentDisposable = parentDisposable;
  }

  @Override
  public final void run() {
    BackgroundTaskUtil.runUnderDisposeAwareIndicator(myParentDisposable, this::doRun);
  }

  protected abstract void doRun();

  
  public static DisposableUpdate createDisposable(@NotNull Disposable parentDisposable,
                                                  @NonNls @NotNull Object identity,
                                                  @NotNull Runnable runnable) {
    return new DisposableUpdate(parentDisposable, identity) {
      @Override
      public void doRun() {
        runnable.run();
      }
    };
  }
}
