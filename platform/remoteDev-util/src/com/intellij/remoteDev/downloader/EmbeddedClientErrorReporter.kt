// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.downloader

/**
 * Used to report errors from JetBrains Client started from IDE distribution via [EmbeddedClientLauncher.launch].
 */
interface EmbeddedClientErrorReporter {
  fun startupFailed(exitCode: Int, output: List<String>)
}