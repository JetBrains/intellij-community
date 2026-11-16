// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.tests

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import git4idea.console.GitConsoleFoldingImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

private const val START_MARKER = "<fold>"
private const val END_MARKER = "</fold>"

class GitConsoleFoldingImplTest {

  private val project: Project = mock(Project::class.java)

  @Test
  fun testFoldings() {
    doTest("")
    doTest("git status")
    doTest("From ssh://git.example.com/project")
    doTest(" - [deleted]                   (none)     -> origin/robot-upstream-merge-2000")
    doTest("remote: Total 306 (delta 162), reused 306 (delta 162)   ")
    doTest("[master acbef1ce] initial commit")

    doTest(
      "12:01:01.010: [Project] git <fold>-c credential.helper= -c http.sslBackend=schannel -c core.quotepath=false" +
      " -c log.showSignature=false</fold> add --ignore-errors -A -f -- file.txt"
    )
    doTest(
      "12:01:01.010: [Project] git <fold>-c credential.helper= -c http.sslBackend=schannel -c core.quotepath=false" +
      " -c log.showSignature=false</fold> commit -F C:\\Users\\User\\AppData\\Local\\Temp\\git-commit-msg.txt --"
    )

    doTest("[Project] git --c core.commentChar=test")
    doTest("[Project] git <fold>-c key=</fold> test")
    doTest("[Project] git <fold>-c key=value</fold> test")
    doTest("[Project] git <fold>-c key=value</fold> key=value <fold>-c key= -c key=value</fold> test")
    doTest("[Project] git <fold>-c core.commentChar=\u0001</fold> test")
    doTest("[Project] git key=value <fold>-c key= -c core.commentChar=\u0001 -c key=</fold> key=value test")

    doTest("[Project] git <fold>-c key=\"long</fold> value\" version") // known issue
  }

  private fun doTest(testData: String) {
    val line = StringBuilder(testData)
    val expectedRanges = mutableListOf<TextRange>()

    var last = 0
    while (last < line.length) {
      val start = line.indexOf(START_MARKER, last)
      if (start == -1) break
      var end = line.indexOf(END_MARKER, start)
      if (end == -1) break

      end -= START_MARKER.length
      line.delete(start, start + START_MARKER.length)
      line.delete(end, end + END_MARKER.length)
      expectedRanges.add(TextRange(start, end))

      last = end
    }

    val provider = GitConsoleFoldingImpl()
    val foldings = provider.getFoldingsForLine(project, line.toString())

    assertEquals(expectedRanges.size, foldings.size)
    for (i in foldings.indices) {
      val expected = expectedRanges[i]
      val actual = foldings[i].textRange
      assertEquals(expected, actual)
    }
  }
}
