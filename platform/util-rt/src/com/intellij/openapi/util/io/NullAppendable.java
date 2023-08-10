// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.io;

import org.jetbrains.annotations.NotNull;

final class NullAppendable implements Appendable {
  static Appendable INSTANCE = new NullAppendable();

  @Override
  @NotNull
  public Appendable append(CharSequence csq) {
    return this;
  }

  @Override
  @NotNull
  public Appendable append(CharSequence csq, int start, int end) {
    return this;
  }

  @Override
  @NotNull
  public Appendable append(char c) {
    return this;
  }
}
