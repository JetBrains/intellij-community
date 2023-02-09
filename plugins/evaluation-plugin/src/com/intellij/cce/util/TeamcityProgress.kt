package com.intellij.cce.util

class TeamcityProgress(private val title: String) : Progress {
  private val cli = CommandLineProgress(title)

  override fun setProgress(fileName: String, text: String, fraction: Double) {
    println("##teamcity[progressMessage '$title: ${text.trim()}']")
    cli.setProgress(fileName, text, fraction)
  }

  override fun start() = println("##teamcity[progressStart '$title']")
  override fun finish() = println("##teamcity[progressFinish '$title']")
  override fun isCanceled() = false
}
