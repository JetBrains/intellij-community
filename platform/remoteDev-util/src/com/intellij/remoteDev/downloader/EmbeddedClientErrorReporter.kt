package com.intellij.remoteDev.downloader

/**
 * Used to report errors from JetBrains Client started from IDE distribution via [EmbeddedClientLauncher.launch].
 */
interface EmbeddedClientErrorReporter {
  fun startupFailed(exitCode: Int, output: List<String>)
}