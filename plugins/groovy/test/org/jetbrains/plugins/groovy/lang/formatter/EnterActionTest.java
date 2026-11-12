/*
 * Copyright 2003-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.formatter;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.formatter.GroovyFormatterFixtureKt.groovyFormatterFixture;

@TestApplication
public class EnterActionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GroovyFormatterFixture> formatter =
    groovyFormatterFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "groovy/enterAction/");

  public void doTest() throws Throwable {
    LightJavaCodeInsightTestFixture myFixture = formatter.get().getFixture();
    final List<String> data = TestUtils.readInput(myFixture.getTestDataPath() + myFixture.getTestName(true) + ".test");
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, data.get(0));
    doEnter();
    myFixture.checkResult(data.get(1));
  }

  private void doEnter() {
    LightJavaCodeInsightTestFixture myFixture = formatter.get().getFixture();
    myFixture.type("\n");
  }

  @Test
  public void testClos1() throws Throwable { doTest(); }

  @Test
  public void testClos2() throws Throwable { doTest(); }

  @Test
  public void testComment1() throws Throwable { doTest(); }

  @Test
  public void testComment2() throws Throwable { doTest(); }

  @Test
  public void testComment3() throws Throwable { doTest(); }

  @Test
  public void testComment4() throws Throwable { doTest(); }

  @Test
  public void testDef() throws Throwable { doTest(); }

  @Test
  public void testDef2() throws Throwable { doTest(); }

  @Test
  public void testGdoc1() throws Throwable { doTest(); }

  @Test
  public void testGdoc2() throws Throwable { doTest(); }

  @Test
  public void testGdoc3() throws Throwable { doTest(); }

  @Test
  public void testGdoc4() throws Throwable { doTest(); }

  @Test
  public void testGdoc5() throws Throwable { doTest(); }

  @Test
  public void testGdoc6() throws Throwable { doTest(); }

  @Test
  public void testGdoc7() throws Throwable { doTest(); }

  @Test
  public void testGdoc8() throws Throwable { doTest(); }

  @Test
  public void testGdoc9() throws Throwable { doTest(); }

  @Test
  public void testGdoc10() throws Throwable { doTest(); }

  @Test
  public void testGdoc11() throws Throwable { doTest(); }

  @Test
  public void testBuildGdocPrecededByGNewLine() throws Throwable { doTest(); }

  @Test
  public void testGRVY_953() throws Throwable { doTest(); }

  @Test
  public void testGstring1() throws Throwable { doTest(); }

  @Test
  public void testGstring10() throws Throwable { doTest(); }

  @Test
  public void testGstring11() throws Throwable { doTest(); }

  @Test
  public void testGstring12() throws Throwable { doTest(); }

  @Test
  public void testGstring13() throws Throwable { doTest(); }

  @Test
  public void testGstring14() throws Throwable { doTest(); }

  @Test
  public void testGstring15() throws Throwable { doTest(); }

  @Test
  public void testGstring16() throws Throwable { doTest(); }

  @Test
  public void testGstring2() throws Throwable { doTest(); }

  @Test
  public void testGstring3() throws Throwable { doTest(); }

  @Test
  public void testGstring4() throws Throwable { doTest(); }

  @Test
  public void testGstring5() throws Throwable { doTest(); }

  @Test
  public void testGstring6() throws Throwable { doTest(); }

  @Test
  public void testGstring7() throws Throwable { doTest(); }

  @Test
  public void testGstring8() throws Throwable { doTest(); }

  @Test
  public void testGstring9() throws Throwable { doTest(); }

  @Test
  public void testMultilineIndent() throws Throwable { doTest(); }

  @Test
  public void testMultilineIndent2() throws Throwable { doTest(); }

  @Test
  public void testSpaces1() throws Throwable { doTest(); }

  @Test
  public void testString1() throws Throwable { doTest(); }

  @Test
  public void testString2() throws Throwable { doTest(); }

  @Test
  public void testString3() throws Throwable { doTest(); }

  @Test
  public void testString4() throws Throwable { doTest(); }

  @Test
  public void testString5() throws Throwable { doTest(); }

  @Test
  public void testString6() throws Throwable { doTest(); }

  @Test
  public void testString7() throws Throwable { doTest(); }

  @Test
  public void testString8() throws Throwable { doTest(); }

  @Test
  public void testString9() throws Throwable { doTest(); }

  @Test
  public void testString10() throws Throwable { doTest(); }

  @Test
  public void testRegex1() throws Throwable { doTest(); }

  @Test
  public void testRegex2() throws Throwable { doTest(); }

  @Test
  public void testRegex3() throws Throwable { doTest(); }

  @Test
  public void testRegex4() throws Throwable { doTest(); }

  @Test
  public void testRegex5() throws Throwable { doTest(); }

  @Test
  public void testRegex6() throws Throwable { doTest(); }

  @Test
  public void testRegex7() throws Throwable { doTest(); }

  @Test
  public void testRegex8() throws Throwable { doTest(); }

  @Test
  public void testRegex9() throws Throwable { doTest(); }

  @Test
  public void testRegex10() throws Throwable { doTest(); }

  public void doTest(String before, String after) {
    LightJavaCodeInsightTestFixture myFixture = formatter.get().getFixture();
    myFixture.configureByText("a.groovy", before);
    doEnter();
    myFixture.checkResult(after, true);
  }

  @Test
  public void testAfterClosureArrow() {
    doTest("""
             def c = { a -><caret> }
             """, """
             def c = { a ->
               <caret>
             }
             """);
  }

  @Test
  public void testAfterClosureArrowWithBody() {
    doTest("""
             def c = { a -><caret> zzz }
             """, """
             def c = { a ->
             <caret>  zzz }
             """);
  }

  @Test
  public void testAfterClosureArrowWithBody2() {
    doTest("""
             def c = { a -> <caret>zzz }
             """, """
             def c = { a ->
               <caret>zzz }
             """);
  }

  @Test
  public void testBeforeClosingClosureBrace() {
    doTest("""
             def c = { a ->
               zzz <caret>}
             """, """
             def c = { a ->
               zzz\s
             <caret>}
             """);
  }

  public void _testAfterLambdaArrow() {
    doTest("""
             def c = a -><caret>
             """, """
             def c = a ->
                 <caret>
             """);
  }

  @Test
  public void testAfterLambdaArrowWithExpressionBody() {
    doTest("""
             def c = a -><caret> zzz
             """, """
             def c = a ->
             <caret>    zzz
             """);
  }

  @Test
  public void testAfterLambdaArrowWithExpressionBody2() {
    doTest("""
             def c = a -> <caret>zzz
             """, """
             def c = a ->
                 <caret>zzz
             """);
  }

  @Test
  public void testAfterLambdaArrowWithBlockBody() {
    doTest("""
             def c = a -> {<caret> zzz}
             """, """ 
             def c = a -> {
               <caret>zzz}
             """);
  }

  @Test
  public void testAfterLambdaArrowWithBlockBody2() {
    doTest("""
             def c = a -> {<caret>}
             """, """
             def c = a -> {
               <caret>
             }
             """);
  }

  @Test
  public void testBeforeClosingLambdaBrace() {
    doTest("""
             def c = a -> {
               zzz <caret>}
             """, """
             def c = a -> {\s
               zzz\s
             <caret>}
             """);
  }

  @Test
  public void testAfterCase() {
    doTest("""
             switch(x) {
               case 0: return x
               case 1:<caret>
             }""", """
             switch(x) {
               case 0: return x
               case 1:
                 <caret>
             }""");
  }

  @Test
  public void testCaseBeforeReturn() {
    doTest("""  
             switch(x) {
               case 0: <caret>
                 return x
             }""", """
             switch(x) {
               case 0:
                 <caret>
                 return x
             }""");
  }

  @Test
  public void testCaseAfterBreak() {
    doTest("""
             switch(x) {
               case 0:
                 break<caret>
             }""", """
             switch(x) {
               case 0:
                 break
               <caret>
             }""");
  }

  @Test
  public void testCaseAfterCall() {
    doTest("""
             switch(x) {
               case 0:
                 foo()<caret>
             }""", """
             switch(x) {
               case 0:
                 foo()
                 <caret>
             }""");
  }

  @Test
  public void testCaseAfterReturn() {
    doTest("""
             switch(x) {
               case 0:
                 return 2<caret>
             }""", """
             switch(x) {
               case 0:
                 return 2
               <caret>
             }""");
  }

  @Test
  public void testWrapInSwitchBlock() {
    doTest("""
             switch(x) {
               case 0 -> {<caret>}
             }
             """, """
             switch(x) {
               case 0 -> {
                 <caret>
               }
             }
             """);
  }

  @Test
  public void testWrapAfterSwitchArrowedExpression() {
    doTest("""
             switch(x) {
               case 0 -> zzz<caret>
             }
             """, """
             switch(x) {
               case 0 -> zzz
               <caret>
             }
             """);
  }

  @Test
  public void testWrapInSwitchExpressionList() {
    doTest("""
             
             switch(x) {
               case 0, <caret>10 -> zzz
             }
             """, """
             
             switch(x) {
               case 0,\s
                   <caret>10 -> zzz
             }
             """);
  }

  @Test
  public void testAlmostBeforeClosingClosureBrace() {
    doTest("""
             def c = { a ->
               zzz<caret> }
             """, """
             def c = { a ->
               zzz
             <caret>}
             """);
  }

  @Test
  public void testEnterWithAlignedParameters() {
    formatter.get().getGroovySettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    doTest("""
             foo(2,<caret>)
             """, """
             foo(2,
                 <caret>)
             """);
  }

  @Test
  public void testEnterWithAlignedParameters2() {
    formatter.get().getGroovySettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    doTest("""
             foo(2,<caret>
             """, """
             foo(2,
                 <caret>
             """);
  }

  @Test
  public void testEnterAfterAssignmentInDeclaration() {
    doTest("""
             def greeting = <caret>
             """, """
             def greeting =
                 <caret>
             """);
  }

  @Test
  public void testEnterInAssignment() {
    doTest("""
             greeting = <caret>
             """, """
             greeting =
                 <caret>
             """);
  }

  @Test
  public void testEnterInIf() {
    doTest("""
             if (2==2) <caret>
             """, """
             if (2==2)
               <caret>
             """);
  }

  @Test
  public void testGeese1() {
    formatter.get().getTempSettings().getCustomSettings(GroovyCodeStyleSettings.class).USE_FLYING_GEESE_BRACES = true;
    doTest("""
             [1, 2, 3, 4].
                 toSet().
                 findAllXxx {
                   it > 2
                   foo {<caret>}
                 }""", """
             [1, 2, 3, 4].
                 toSet().
                 findAllXxx {
                   it > 2
                   foo {
                     <caret>
                 } }""");
  }

  @Test
  public void testGeese2() {
    formatter.get().getTempSettings().getCustomSettings(GroovyCodeStyleSettings.class).USE_FLYING_GEESE_BRACES = true;
    doTest("""
             foo {
               bar {<caret>}
             }
             """, """
             foo {
               bar {
                 <caret>
             } }
             """);
  }

  @Test
  public void testGeese3() {
    formatter.get().getTempSettings().getCustomSettings(GroovyCodeStyleSettings.class).USE_FLYING_GEESE_BRACES = true;
    doTest("""
             foo {
               [1].bar {<caret>}
             }
             """, """
             foo {
               [1].bar {
                 <caret>
             } }
             """);
  }

  @Test
  public void testEnterAfterSlash() {
    doTest("""
             print '\\<caret>'
             """, """
             print '\\
             <caret>'
             """);
  }

  @Test
  public void testEnterAfterSlash2() {
    doTest("""
             print "\\<caret>"
             """, """
             print "\\
             <caret>"
             """);
  }

  @Test
  public void test_enter_after_doc_with_wrong_braces() {
    doTest("""
             /**
              * {@link #z
              */
             protected void z() {<caret>
             }""", """
             /**
              * {@link #z
              */
             protected void z() {
               <caret>
             }""");
  }

  @Test
  public void testAfterArrow() {
    doTest("""
             def cl =  {
               String s -><caret>
             }
             """, """
             def cl =  {
               String s ->
                 <caret>
             }
             """);
  }

  @Test
  public void testIndentAfterLabelColon() {
    formatter.get().getGroovySettings().getIndentOptions().LABEL_INDENT_SIZE = 2;
    formatter.get().getGroovySettings().getIndentOptions().INDENT_SIZE = 2;
    formatter.get().getGroovyCustomSettings().INDENT_LABEL_BLOCKS = true;
    doTest("""
             class A extends spock.lang.Specification {
               def 'test'() {
                 abc:<caret>
               }
             }
             """, """
             class A extends spock.lang.Specification {
               def 'test'() {
                 abc:
                   <caret>
               }
             }
             """);
  }

  @Test
  public void testCommentAtFileEnd() {
    doTest("""
             print 2
             /*<caret>""", """
             print 2
             /*
             <caret>
              */""");
  }

  @Test
  public void testIfCondition() {
    doTest("if (<caret>true) {}", """
      if (
          true) {}""");
  }
}
