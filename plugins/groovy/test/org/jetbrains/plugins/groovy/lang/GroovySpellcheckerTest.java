// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang;

import com.intellij.grazie.spellcheck.GrazieSpellCheckingInspection;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class GroovySpellcheckerTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testParameterName() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.groovy", """
      def test(int <TYPO descr="Typo: In word 'ddddd'">ddddd</TYPO>) {
      }
      """);
    checkTypos();
  }

  private void checkTypos() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new GrazieSpellCheckingInspection());
    myFixture.checkHighlighting(false, false, true);
  }

  @Test
  public void testLiteralMethodNames() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.groovy", """
      class SpockTest {
        def "adds a 'play' extension"() { }
        def "<TYPO descr="Typo: In word 'addds'">addds</TYPO> a 'play' extension"() { }
      }
      """);
    checkTypos();
  }

  @Test
  public void testStringEscapes() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.groovy", """
      def foo = "\\ntest \\n<TYPO descr="Typo: In word 'ddddd'">ddddd</TYPO>"
      def foo1 = '\\ntest \\n<TYPO descr="Typo: In word 'ddddd'">ddddd</TYPO>'
      def bar = ""\"\\ntest \\n<TYPO descr="Typo: In word 'ddddd'">ddddd</TYPO>""\"
      def bar1 = '''\\ntest \\n<TYPO descr="Typo: In word 'ddddd'">ddddd</TYPO>'''
      """);
    checkTypos();
  }
}
