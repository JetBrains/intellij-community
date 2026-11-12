package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyFixtures;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;

@TestApplication
public class CodeBlockGenerationTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture, CodeBlockGenerationTestUtil.TEST_DATA_PATH);

  @Test
  public void testSwitch1() { CodeBlockGenerationTestUtil.doTest(codeInsightFixture.get()); }

  @Test
  public void testSwitch2() { CodeBlockGenerationTestUtil.doTest(codeInsightFixture.get()); }

  @Test
  public void testSwitch3() { CodeBlockGenerationTestUtil.doTest(codeInsightFixture.get()); }

  @Test
  public void testSwitch4() { CodeBlockGenerationTestUtil.doTest(codeInsightFixture.get()); }

  @Test
  public void testSwitch5() { CodeBlockGenerationTestUtil.doTest(codeInsightFixture.get()); }

  @Test
  public void testSwitch6() { CodeBlockGenerationTestUtil.doTest(codeInsightFixture.get()); }

  public void _testWhile1() { CodeBlockGenerationTestUtil.doTest(codeInsightFixture.get()); }

  public void _testWhile2() { CodeBlockGenerationTestUtil.doTest(codeInsightFixture.get()); }

  public void _testWhile3() { CodeBlockGenerationTestUtil.doTest(codeInsightFixture.get()); }

  @Test
  public void testRefExpr() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("Bar.groovy", """

      class Bar {
        def foo = 2

        def getBar() {3}
      }
      class MyCat {
        static getAbc(Bar b) {
          return 4
        }
      }
      """);

    CodeBlockGenerationTestUtil.doTest(myFixture);
  }

  @Test
  public void testMemberPointer() { CodeBlockGenerationTestUtil.doTest(codeInsightFixture.get()); }

  @Test
  public void testCompareMethods() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    CodeBlockGenerationTestUtil.addFile(myFixture, """

              class Bar {
                def compareTo(def other) {1}
              }""");
  }

  @Test
  public void testPrefixInc() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    CodeBlockGenerationTestUtil.addFile(myFixture, """

              class Bar {
                  def next() {
                      new Bar(state:state+1)
                  }
              }

              class Upper {
                  private test=new Test()
                  def getTest(){println "getTest"; test}
              }

              class Test {
                  public state = new Bar()
                  def getBar() {println "get"; state}
                  void setBar(def bar) {println "set";state = bar}
              }
              """);
    CodeBlockGenerationTestUtil.doTest(myFixture);
  }

  @Test
  public void testUnaryMethods() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    CodeBlockGenerationTestUtil.addFile(myFixture, """

              class Bar {
                def positive(){}
                def negative(){}
                def bitwiseNegate(){}
              }""");
  }

  @Test
  public void testRegex() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("java/util/regex/Pattern.java", """

      package java.util.regex;

      public final class Pattern {
        public static Pattern compile(String regex) {return new Pattern();}
        public Matcher matcher(CharSequence input){return new Matcher();}
        public static boolean matches(String regex, CharSequence input) {return true;}
      }""");

    myFixture.addFileToProject("java/util/regex/Matcher.java", """

      package java.util.regex;

      public final class Matcher {
        public boolean matches() {return true;}
      }

      """);
    CodeBlockGenerationTestUtil.doTest(myFixture);
  }

  @Test
  public void testAsBoolean() { CodeBlockGenerationTestUtil.doTest(codeInsightFixture.get()); }

  @Test
  public void testCharInitializer() { CodeBlockGenerationTestUtil.doTest(codeInsightFixture.get()); }

  @Test
  public void testAnonymousFromMap() { CodeBlockGenerationTestUtil.doTest(codeInsightFixture.get()); }

  @Test
  public void testClosure() { CodeBlockGenerationTestUtil.doTest(codeInsightFixture.get()); }

  @Test
  public void testListAsArray() { CodeBlockGenerationTestUtil.doTest(codeInsightFixture.get()); }

  @Test
  public void testUnresolvedArrayAccess() { CodeBlockGenerationTestUtil.doTest(codeInsightFixture.get()); }

  @Test
  public void testArrayAccess() { CodeBlockGenerationTestUtil.doTest(codeInsightFixture.get()); }

  @Test
  public void testCastWithEquality() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    LightGroovyFixtures.addBigDecimal(myFixture);
    CodeBlockGenerationTestUtil.doTest(myFixture);
  }

  @Test
  public void testAsserts() { CodeBlockGenerationTestUtil.doTest(codeInsightFixture.get()); }

  @Test
  public void testConditional() { CodeBlockGenerationTestUtil.doTest(codeInsightFixture.get()); }

  @Test
  public void testBinary() { CodeBlockGenerationTestUtil.doTest(codeInsightFixture.get()); }

  @Test
  public void testSafeCast() { CodeBlockGenerationTestUtil.doTest(codeInsightFixture.get()); }

  @Test
  public void testNewExpr() { CodeBlockGenerationTestUtil.doTest(codeInsightFixture.get()); }

  @Test
  public void testMapNameAlreadyused() { CodeBlockGenerationTestUtil.doTest(codeInsightFixture.get()); }

  @Test
  public void testEmptyMap() { CodeBlockGenerationTestUtil.doTest(codeInsightFixture.get()); }

  @Test
  public void testEmptyList() { CodeBlockGenerationTestUtil.doTest(codeInsightFixture.get()); }

  @Test
  public void testErasedArrayInitializer() { CodeBlockGenerationTestUtil.doTest(codeInsightFixture.get()); }

  @Test
  public void testTupleVariableDeclaration() { CodeBlockGenerationTestUtil.doTest(codeInsightFixture.get()); }

  @Test
  public void testEmptyVarargs() { CodeBlockGenerationTestUtil.doTest(codeInsightFixture.get()); }

  @Test
  public void testClassReference() { CodeBlockGenerationTestUtil.doTest(codeInsightFixture.get()); }

  @Test
  public void testClassMethod() { CodeBlockGenerationTestUtil.doTest(codeInsightFixture.get()); }

  @Test
  public void testEquals() { CodeBlockGenerationTestUtil.doTest(codeInsightFixture.get()); }

  @Test
  public void testSelfNavigatingOperator() { CodeBlockGenerationTestUtil.doTest(codeInsightFixture.get()); }

  @Test
  public void testComparisonToNull() { CodeBlockGenerationTestUtil.doTest(codeInsightFixture.get()); }

  @Test
  public void testStringConverting() { CodeBlockGenerationTestUtil.doTest(codeInsightFixture.get()); }

  @Test
  public void testMultilineString() { CodeBlockGenerationTestUtil.doTest(codeInsightFixture.get()); }

  @Test
  public void testInWitchClassCheck() { CodeBlockGenerationTestUtil.doTest(codeInsightFixture.get()); }

  @Test
  public void testSwitch() { CodeBlockGenerationTestUtil.doTest(codeInsightFixture.get()); }

  @Test
  public void testPropSelection() { CodeBlockGenerationTestUtil.doTest(codeInsightFixture.get()); }

  @Test
  public void testSafeChain() { CodeBlockGenerationTestUtil.doTest(codeInsightFixture.get()); }
}
