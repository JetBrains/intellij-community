package org.jetbrains.plugins.groovy.refactoring.convertToJava;

public class CodeBlockGenerationTest extends CodeBlockGenerationBaseTest {
  public void testSwitch1() { doTest(); }

  public void testSwitch2() { doTest(); }

  public void testSwitch3() { doTest(); }

  public void testSwitch4() { doTest(); }

  public void testSwitch5() { doTest(); }

  public void testSwitch6() { doTest(); }

  public void _testWhile1() { doTest(); }

  public void _testWhile2() { doTest(); }

  public void _testWhile3() { doTest(); }

  public void testRefExpr() {
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

    doTest();
  }

  public void testMemberPointer() { doTest(); }

  public void testCompareMethods() {
    addFile("""

              class Bar {
                def compareTo(def other) {1}
              }""");
  }

  public void testPrefixInc() {
    addFile("""

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
    doTest();
  }

  public void testUnaryMethods() {
    addFile("""

              class Bar {
                def positive(){}
                def negative(){}
                def bitwiseNegate(){}
              }""");
  }

  public void testRegex() {
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
    doTest();
  }

  public void testAsBoolean() { doTest(); }

  public void testCharInitializer() { doTest(); }

  public void testAnonymousFromMap() { doTest(); }

  public void testClosure() { doTest(); }

  public void testListAsArray() { doTest(); }

  public void testUnresolvedArrayAccess() { doTest(); }

  public void testArrayAccess() { doTest(); }

  public void testCastWithEquality() {
    addBigDecimal();
    doTest();
  }

  public void testAsserts() { doTest(); }

  public void testConditional() { doTest(); }

  public void testBinary() { doTest(); }

  public void testSafeCast() { doTest(); }

  public void testNewExpr() { doTest(); }

  public void testMapNameAlreadyused() { doTest(); }

  public void testEmptyMap() { doTest(); }

  public void testEmptyList() { doTest(); }

  public void testErasedArrayInitializer() { doTest(); }

  public void testTupleVariableDeclaration() { doTest(); }

  public void testEmptyVarargs() { doTest(); }

  public void testClassReference() { doTest(); }

  public void testClassMethod() { doTest(); }

  public void testEquals() { doTest(); }

  public void testSelfNavigatingOperator() { doTest(); }

  public void testComparisonToNull() { doTest(); }

  public void testStringConverting() { doTest(); }

  public void testMultilineString() { doTest(); }

  public void testInWitchClassCheck() { doTest(); }

  public void testSwitch() { doTest(); }

  public void testPropSelection() { doTest(); }

  public void testSafeChain() { doTest(); }
}
