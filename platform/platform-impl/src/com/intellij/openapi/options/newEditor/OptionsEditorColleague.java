// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
    @Override
    public @NotNull Promise<? super Object> onSelected(final @Nullable Configurable configurable, final Configurable oldConfigurable) {
      return Promises.resolvedPromise();
    }

    @Override
    public @NotNull Promise<? super Object> onModifiedAdded(final Configurable configurable) {
      return Promises.resolvedPromise();
    }

    @Override
    public @NotNull Promise<? super Object> onModifiedRemoved(final Configurable configurable) {
      return Promises.resolvedPromise();
    }

    @Override
    public @NotNull Promise<? super Object> onErrorsChanged() {
      return Promises.resolvedPromise();
    }
  }
}