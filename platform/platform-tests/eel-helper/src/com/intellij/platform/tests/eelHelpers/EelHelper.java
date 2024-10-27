// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.tests.eelHelpers;

import org.jetbrains.annotations.TestOnly;

import static com.intellij.platform.tests.eelHelpers.network.NetworkHelperKt.startNetworkHelper;
import static com.intellij.platform.tests.eelHelpers.ttyAndExit.TtyAndExitHelperKt.startTtyAndExitHelper;

/**
 * Helper that is run by EEL test: should react on signals and commands
 */
@TestOnly
public final class EelHelper {
  private EelHelper() {
  }

  @TestOnly
  public static void main(String[] args) {
    if (args.length == 0) {
      startTtyAndExitHelper();
    }
    else {
      startNetworkHelper();
    }
  }
}
