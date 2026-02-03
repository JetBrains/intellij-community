// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.ex;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.ConfigurableGroup;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public interface MutableConfigurableGroup extends ConfigurableGroup, Disposable {
  interface Listener {
    void handleUpdate();
  }

  void addListener(@NotNull Listener listener);
}