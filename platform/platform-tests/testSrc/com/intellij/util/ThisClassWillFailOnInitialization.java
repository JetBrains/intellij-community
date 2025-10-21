// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import kotlin.jvm.JvmStatic;

public class ThisClassWillFailOnInitialization {

  static {
    willFail();
  }

  private static void willFail() {
    throw new RuntimeException("This class will fail on initialization");
  }
}
