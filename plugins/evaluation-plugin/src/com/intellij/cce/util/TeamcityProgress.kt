// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.util

class TeamcityProgress(private val title: String) : Progress {
  private val cli = CommandLineProgress(title)

  override fun setProgress(fileName: String, text: String, fraction: Double) {
    val percent = (fraction * 100).toInt()

    println("##teamcity[progressMessage '$title: $percent% ${text.trim()}']")
    cli.setProgress(fileName, text, fraction)
  }

  override fun start() = println("##teamcity[progressStart '$title']")
  override fun finish() = println("##teamcity[progressFinish '$title']")
  override fun isCanceled() = false
}
