package com.intellij.cce.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.intellij.openapi.extensions.ExtensionPointName

interface EvaluationCommandExtension {
  companion object {
    val EP_NAME: ExtensionPointName<EvaluationCommandExtension> = ExtensionPointName.create("com.intellij.cce.command")
  }
  fun extend(command: CliktCommand)
}

val evaluationCommandExtensions: List<EvaluationCommandExtension>
  get(): List<EvaluationCommandExtension> = EvaluationCommandExtension.EP_NAME.extensionList
