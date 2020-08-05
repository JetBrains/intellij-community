// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.console

import com.intellij.execution.ConsoleFolding
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.vcs.console.VcsConsoleFolding
import com.intellij.vcs.console.VcsConsoleFolding.Placeholder
import git4idea.commands.GitImplBase
import java.util.regex.Matcher
import java.util.regex.Pattern

class GitConsoleFolding : VcsConsoleFolding {
  override fun getFoldingsForLine(project: Project, line: String): List<Placeholder> {
    if (!isGitCommandLine(line)) return emptyList()

    val result = mutableListOf<Placeholder>()
    val matcher: Matcher = CONFIG_OPTIONS_REGEX.matcher(line)
    while (matcher.find()) {
      var start = matcher.start()
      val end = matcher.end()
      if (start < end && StringUtil.isWhiteSpace(line[start])) start++

      if (start < end) {
        result.add(Placeholder("-c ...", TextRange(start, end)))
      }
    }

    return result
  }

  private fun isGitCommandLine(line: String): Boolean {
    return GIT_LINE_REGEX.matcher(line).find()
  }

  companion object {
    private val CONFIG_OPTIONS_REGEX: Pattern = Pattern.compile("(\\s-c\\s[\\w.]+=[^ ]*)+")
    private val GIT_LINE_REGEX: Pattern = Pattern.compile("\\[.*] git ")
  }
}

class GitProgressOutputConsoleFolding : ConsoleFolding() {
  override fun shouldBeAttachedToThePreviousLine(): Boolean = false

  override fun getPlaceholderText(project: Project, lines: MutableList<String>): String? {
    return lines.lastOrNull()
  }

  override fun shouldFoldLine(project: Project, line: String): Boolean {
    return GitImplBase.looksLikeProgress(line)
  }
}