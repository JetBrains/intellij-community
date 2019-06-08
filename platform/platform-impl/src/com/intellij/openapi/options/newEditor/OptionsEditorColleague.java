// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.newEditor;

import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

public interface OptionsEditorColleague {
  @NotNull
  Promise<? super Object> onSelected(@Nullable Configurable configurable, final Configurable oldConfigurable);

  @NotNull
  Promise<? super Object> onModifiedAdded(final Configurable configurable);

  @NotNull
  Promise<? super Object> onModifiedRemoved(final Configurable configurable);

  @NotNull
  Promise<? super Object> onErrorsChanged();

  class Adapter implements OptionsEditorColleague {
    @NotNull
    @Override
    public Promise<? super Object> onSelected(@Nullable final Configurable configurable, final Configurable oldConfigurable) {
      return Promises.resolvedPromise();
    }

    @NotNull
    @Override
    public Promise<? super Object> onModifiedAdded(final Configurable configurable) {
      return Promises.resolvedPromise();
    }

    @NotNull
    @Override
    public Promise<? super Object> onModifiedRemoved(final Configurable configurable) {
      return Promises.resolvedPromise();
    }

    @NotNull
    @Override
    public Promise<? super Object> onErrorsChanged() {
      return Promises.resolvedPromise();
    }
  }
}