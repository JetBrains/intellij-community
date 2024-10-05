// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.tests.eelHelper;

import org.jetbrains.annotations.TestOnly;

import static com.intellij.platform.tests.eelHelper.ImplKt.startHelper;

/**
 * Helper that is run by EEL test: should react on signals and commands
 */
@TestOnly
public final class EelHelper {
  private EelHelper() {
  }

  @TestOnly
  public static void main(String[] args) {
    startHelper();
  }
}
