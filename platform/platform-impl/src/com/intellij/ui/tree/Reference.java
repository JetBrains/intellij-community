// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree;

final class Reference<T> {
  private volatile boolean valid;
  private volatile T value;

  boolean isValid() {
    return valid;
  }

  void invalidate() {
    valid = false;
  }

  T set(T value) {
    T old = this.value;
    this.value = value;
    valid = true;
    return old;
  }

  T get() {
    return value;
  }
}
