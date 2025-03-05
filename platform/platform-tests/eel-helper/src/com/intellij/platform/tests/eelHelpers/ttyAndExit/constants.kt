// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.tests.eelHelpers.ttyAndExit

import org.jetbrains.annotations.TestOnly

/**
 * Messages to be printed to stderr by this helper
 */
@TestOnly
const val HELLO: String = "hello"

/**
 * Exit code to be used on `SIGINT`
 */

@TestOnly
const val INTERRUPT_EXIT_CODE: Int = 42

/**
 * Exit code for [Command.EXIT]
 */
@TestOnly
const val GRACEFUL_EXIT_CODE: Int = 0
