// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.tests.eelHelpers;

import org.jetbrains.annotations.TestOnly;

import static com.intellij.platform.tests.eelHelpers.network.NetworkHelperKt.startNetworkServer;
import static com.intellij.platform.tests.eelHelpers.network.NetworkHelperKt.startNetworkClient;
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
    HelperMode mode = HelperMode.valueOf(args[0]);
    switch (mode) {
      case TTY -> {
        startTtyAndExitHelper();
      }
      case NETWORK_SERVER -> {
        startNetworkServer();
      }
      case NETWORK_CLIENT -> {
        startNetworkClient();
      }
    }
  }

  public enum HelperMode {
    TTY, NETWORK_SERVER, NETWORK_CLIENT
  }
}
