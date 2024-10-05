// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.tests.eelHelper

import org.jetbrains.annotations.TestOnly

/**
 * Commands to be sent from test to this helper
 */
@TestOnly
enum class Command {
  /**
   * Exit with [GRACEFUL_EXIT_CODE]
   */
  EXIT,

  /**
   * Sleep for a long time
   */
  SLEEP
}