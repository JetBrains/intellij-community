// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.io;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

class NullAppendable implements Appendable {
  static Appendable INSTANCE = new NullAppendable();

  @NotNull
  public Appendable append(CharSequence csq) throws IOException {
    return this;
  }

  @NotNull
  public Appendable append(CharSequence csq, int start, int end) throws IOException {
    return this;
  }

  @NotNull
  public Appendable append(char c) throws IOException {
    return this;
  }
}
