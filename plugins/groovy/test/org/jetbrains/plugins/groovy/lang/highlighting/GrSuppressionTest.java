// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.grazie.spellcheck.GrazieSpellCheckingInspection;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.util.HighlightingTest;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;

@TestApplication
public class GrSuppressionTest implements HighlightingTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_5);
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy = groovyFixture(project);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  private void doTest(String before, String after) {
    JavaCodeInsightTestFixture fixture = getFixture();
    fixture.enableInspections(GrazieSpellCheckingInspection.class);
    configureByText(before);
    fixture.checkHighlighting();
    runInEdtAndWait(() -> fixture.launchAction(fixture.getAvailableIntention(AnalysisBundle.message("suppress.inspection.file"))));
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
