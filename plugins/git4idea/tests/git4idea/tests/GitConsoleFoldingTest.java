// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.tests;

import com.intellij.openapi.util.TextRange;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.vcs.console.VcsConsoleFolding;
import git4idea.console.GitConsoleFolding;

import java.util.ArrayList;
import java.util.List;

public class GitConsoleFoldingTest extends LightPlatformTestCase {
  private static final String START_MARKER = "<fold>";
  private static final String END_MARKER = "</fold>";

  public void testFoldings() {
    doTest("");
    doTest("git status");
    doTest("From ssh://git.example.com/project");
    doTest(" - [deleted]                   (none)     -> origin/robot-upstream-merge-2000");
    doTest("remote: Total 306 (delta 162), reused 306 (delta 162)   ");
    doTest("[master acbef1ce] initial commit");

    doTest("12:01:01.010: [Project] git <fold>-c credential.helper= -c http.sslBackend=schannel -c core.quotepath=false" +
           " -c log.showSignature=false</fold> add --ignore-errors -A -f -- file.txt");
    doTest("12:01:01.010: [Project] git <fold>-c credential.helper= -c http.sslBackend=schannel -c core.quotepath=false" +
           " -c log.showSignature=false</fold> commit -F C:\\Users\\User\\AppData\\Local\\Temp\\git-commit-msg.txt --");

    doTest("[Project] git --c core.commentChar=test");
    doTest("[Project] git <fold>-c key=</fold> test");
    doTest("[Project] git <fold>-c key=value</fold> test");
    doTest("[Project] git <fold>-c key=value</fold> key=value <fold>-c key= -c key=value</fold> test");
    doTest("[Project] git <fold>-c core.commentChar=\u0001</fold> test");
    doTest("[Project] git key=value <fold>-c key= -c core.commentChar=\u0001 -c key=</fold> key=value test");

    doTest("[Project] git <fold>-c key=\"long</fold> value\" version"); // known issue
  }

  private void doTest(String testData) {
    StringBuilder line = new StringBuilder(testData);
    List<TextRange> expectedRanges = new ArrayList<>();

    int last = 0;
    while (last < line.length()) {
      int start = line.indexOf(START_MARKER, last);
      if (start == -1) break;
      int end = line.indexOf(END_MARKER, start);
      if (end == -1) break;

      end -= START_MARKER.length();
      line.delete(start, start + START_MARKER.length());
      line.delete(end, end + END_MARKER.length());
      expectedRanges.add(new TextRange(start, end));

      last = end;
    }

    GitConsoleFolding provider = new GitConsoleFolding();
    List<VcsConsoleFolding.Placeholder> foldings = provider.getFoldingsForLine(getProject(), line.toString());

    assertEquals(expectedRanges.size(), foldings.size());
    for (int i = 0; i < foldings.size(); i++) {
      TextRange expected = expectedRanges.get(i);
      TextRange actual = foldings.get(i).getTextRange();
      assertEquals(expected, actual);
    }
  }
}
