// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commands

import com.intellij.execution.process.AnsiEscapeDecoder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Pair
import git4idea.util.GitVcsConsoleWriter
import java.nio.file.Path

internal class GitCommandOutputConsolePrinter(private val project: Project) : GitCommandOutputPrinter {
  private val ansiEscapeDecoder by lazy { AnsiEscapeDecoder() }

  override fun showCommandStart(processId: String, workingDir: Path, commandLine: String) {
    val commandLineWithDir = String.format("[%s] %s", workingDir, commandLine)
    GitVcsConsoleWriter.getInstance(project).showCommandLine(commandLineWithDir)
  }

  override fun showCommandOutput(processId: String, workingDir: Path, outputType: Key<*>, line: String) {
    val lineChunks = mutableListOf<Pair<String?, Key<*>>>()
    //TODO: async processing as stated in AnsiEscapeDecoder documentation
    ansiEscapeDecoder.escapeText(line, outputType) { text, key -> lineChunks.add(Pair.create(text, key)) }
    GitVcsConsoleWriter.getInstance(project).showMessage(lineChunks);
  }

  override fun showCommandFinished(processId: String, workingDir: Path, exitCode: Int) = Unit
}