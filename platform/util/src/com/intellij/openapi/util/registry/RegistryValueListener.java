// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.registry;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;

/**
 * Listener for receiving notifications about registry value state changes.
 * Use {@link RegistryValue#addListener(RegistryValueListener, Disposable)} to register a listener.
 */
public interface RegistryValueListener {
  default void beforeValueChanged(@NotNull RegistryValue value) {
  }

  default void afterValueChanged(@NotNull RegistryValue value) {
  }
}