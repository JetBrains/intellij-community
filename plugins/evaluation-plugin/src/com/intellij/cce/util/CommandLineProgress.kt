// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.util

class CommandLineProgress(private val title: String) : Progress {
  companion object {
    private const val progressLength = 20
  }

  private val progress = StringBuilder(140)
  private var currentFile = ""
  private var currentPercent = 0

  override fun setProgress(fileName: String, text: String, fraction: Double) {
    val percent = (fraction * 100).toInt()
    if (percent == currentPercent && currentFile == fileName) return

    currentPercent = percent
    currentFile = fileName

    progress.clear()
    val doneParts = (fraction * (progressLength - 1)).toInt()
    progress
      .append('\r')
      .append("$title:")
      .append("${percent.toString().padStart(3)}%")
      .append(" [")
      .append("=".repeat(doneParts))
      .append('>')
      .append(" ".repeat(progressLength - doneParts - 1))
      .append("] $text")
    println(progress)
  }

  override fun isCanceled(): Boolean = false
}
