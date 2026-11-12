// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.strings;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.intentions.GrIntentionFixture;
import org.jetbrains.plugins.groovy.intentions.conversions.strings.ConvertStringToMultilineIntention;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.intentions.GrIntentionFixtureKt.grIntentionFixture;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Max Medvedev
 */
@TestApplication
public class ConvertStringToMultilineTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GrIntentionFixture> grIntention =
    grIntentionFixture(projectFixture, ConvertStringToMultilineIntention.getHint(), TestUtils.getAbsoluteTestDataPath() + "intentions/convertToMultiline/");

  @Test
  public void testPlainString() {
    grIntention.get().doTextTest("print 'ab<caret>c'", "print '''abc'''");
  }

  @Test
  public void testGString() {
    grIntention.get().doTextTest("print \"ab<caret>c\"", "print \"\"\"abc\"\"\"");
  }

  @Test
  public void testPlainString2() {
    grIntention.get().doTextTest("print 'a\\nb<caret>c'", "print '''a\nbc'''");
  }

  @Test
  public void testGString2() {
    grIntention.get().doTextTest("print \"a\\nb<caret>c\"", "print \"\"\"a\nbc\"\"\"");
  }

  @Test
  public void testGString3() {
    grIntention.get().doTextTest("print \"a\\nb${a<caret>}c\"", "print \"\"\"a\nb${a}c\"\"\"");
  }

  @Test
  public void testGString4() {
    grIntention.get().doTextTest("""
                 print "ab<caret>c\\$ $x"
                 """, """
                 print ""\"abc\\$ $x""\"
                 """);
  }

  @Test
  public void testEscapeEndingQuote() {
    grIntention.get().doTextTest("""
                 void test() {
                     "<caret>1\\""
                 }""",
               """
                 void test() {
                     ""\"1\\""\""
                 }""");
  }

  @Test
  public void testAlreadyMultiline() {
    grIntention.get().doAntiTest("print \"\"\"a<caret>bc\"\"\"");
  }

  @Test
  public void testNonConstantConcatenation() {
    grIntention.get().doAntiTest("""
                 def bug() {
                     String s = 'x'
                     <caret>s + "12"; // move caret before "s"
                 }
                 """);
  }

  public void doSelectionTest(String before, String after) {
    LightJavaCodeInsightTestFixture myFixture = grIntention.get().getFixture();
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, before);

    EdtTestUtil.runInEdtAndWait(() -> {
      assertTrue(myFixture.getEditor().getSelectionModel().hasSelection());
      final IntentionAction intention = new ConvertStringToMultilineIntention();
      assertTrue(intention.isAvailable(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile()));
      intention.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());

      myFixture.checkResult(after);
    });
  }

  @Test
  public void testSimpleConcatenation() {
    doSelectionTest("""
                      print <selection>'foo\\n' +
                            'bar'</selection>
                      """, """
                      print <selection>'''foo
                      bar'''</selection>
                      """);
  }

  @Test
  public void testGStringConcatenation() {
    doSelectionTest("""
                      print <selection>"foo${x}\\n" +
                            "bar"</selection>
                      """, """
                      print <selection>""\"foo${x}
                      bar""\"</selection>
                      """);
  }
}
