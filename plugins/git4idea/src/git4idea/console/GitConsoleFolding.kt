// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.console

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.vcs.console.VcsConsoleFolding
import java.util.regex.Matcher
import java.util.regex.Pattern

class GitConsoleFolding : VcsConsoleFolding {
  override fun getFoldingsForLine(project: Project, line: String): List<VcsConsoleFolding.Placeholder> {
    if (!isGitCommandLine(line)) return emptyList()

    val matcher: Matcher = CONFIG_OPTIONS_REGEX.matcher(line)
    while (matcher.find()) {
      val start = matcher.start()
      var end = matcher.end()
      if (start < end && StringUtil.isWhiteSpace(line[end - 1])) end--

      if (start < end) {
        return listOf(VcsConsoleFolding.Placeholder("-c ...", TextRange(start, end)))
      }
    }

    return emptyList()
  }

  private fun isGitCommandLine(line: String): Boolean {
    return GIT_LINE_REGEX.matcher(line).find()
  }

  companion object {
    private val CONFIG_OPTIONS_REGEX: Pattern = Pattern.compile("(-c\\s[\\w.]+=(?:[\\w.]+|)\\s?)+")
    private val GIT_LINE_REGEX: Pattern = Pattern.compile("\\[.*] git ")
  }
}