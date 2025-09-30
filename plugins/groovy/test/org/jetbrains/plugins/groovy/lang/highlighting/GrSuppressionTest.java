// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.grazie.spellcheck.GrazieSpellCheckingInspection;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import org.jetbrains.plugins.groovy.util.GroovyLatestTest;
import org.jetbrains.plugins.groovy.util.HighlightingTest;
import org.junit.Test;

public class GrSuppressionTest extends GroovyLatestTest implements HighlightingTest {
  private void doTest(String before, String after) {
    JavaCodeInsightTestFixture fixture = getFixture();
    fixture.enableInspections(GrazieSpellCheckingInspection.class);
    configureByText(before);
    fixture.checkHighlighting();
    fixture.launchAction(fixture.getAvailableIntention(AnalysisBundle.message("suppress.inspection.file")));
    fixture.checkResult(after);
  }

  @Test
  public void suppressByFileLevelComment() {
    doTest("""



             println("<TYPO><caret>abcdef</TYPO>")
             """, """
             //file:noinspection SpellCheckingInspection



             println("abcdef")
             """);
  }

  @Test
  public void suppressByFileLevelCommentAfterAnotherComment() {
    doTest("""
             /* some other comment */



             println("<TYPO><caret>abcdef</TYPO>")
             """, """
             /* some other comment */
             //file:noinspection SpellCheckingInspection



             println("abcdef")
             """);
  }

  @Test
  public void suppressByFileLevelCommentAfterHashBang() {
    doTest("""
             #!/usr/bin/env groovy
             // some other comment



             println("<TYPO><caret>abcdef</TYPO>")
             """, """
             #!/usr/bin/env groovy
             // some other comment
             //file:noinspection SpellCheckingInspection



             println("abcdef")
             """);
  }
}
