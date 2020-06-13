// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.rt.junit;

import java.io.OutputStream;
import java.io.PrintStream;

public class DeafStream extends OutputStream {
  public static final DeafStream CURRENT = new DeafStream();
  public static final PrintStream DEAF_PRINT_STREAM = new PrintStream(CURRENT);

  @Override
  public void write(int b) {
  }
}
