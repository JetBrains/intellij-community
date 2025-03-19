// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang;

import com.intellij.spellchecker.inspections.SpellCheckingInspection;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class GroovySpellcheckerTest extends LightJavaCodeInsightFixtureTestCase {
  public void testParameterName() {
    myFixture.configureByText("a.groovy", """
      def test(int <TYPO descr="Typo: In word 'ddddd'">ddddd</TYPO>) {
      }
      """);
    checkTypos();
  }

  private void checkTypos() {
    myFixture.enableInspections(new SpellCheckingInspection());
    myFixture.checkHighlighting(false, false, true);
  }

  public void testLiteralMethodNames() {
    myFixture.configureByText("a.groovy", """
      class SpockTest {
        def "adds a 'play' extension"() { }
        def "<TYPO descr="Typo: In word 'addds'">addds</TYPO> a 'play' extension"() { }
      }
      """);
    checkTypos();
  }

  public void testStringEscapes() {
    myFixture.configureByText("a.groovy", """
      def foo = "\\ntest \\n<TYPO descr="Typo: In word 'ddddd'">ddddd</TYPO>"
      def foo1 = '\\ntest \\n<TYPO descr="Typo: In word 'ddddd'">ddddd</TYPO>'
      def bar = ""\"\\ntest \\n<TYPO descr="Typo: In word 'ddddd'">ddddd</TYPO>""\"
      def bar1 = '''\\ntest \\n<TYPO descr="Typo: In word 'ddddd'">ddddd</TYPO>'''
      """);
    checkTypos();
  }
}
