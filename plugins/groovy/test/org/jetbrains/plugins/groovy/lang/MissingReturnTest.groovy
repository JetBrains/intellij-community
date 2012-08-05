package org.jetbrains.plugins.groovy.lang;


import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.noReturnMethod.MissingReturnInspection
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author peter
 */
public class MissingReturnTest extends LightGroovyTestCase {

  @Override
  protected String getBasePath() {
    return "${TestUtils.testDataPath}highlighting/missingReturn";
  }

  public void testMissingReturnWithLastLoop() throws Throwable { doTest(); }
  public void testMissingReturnWithUnknownCall() throws Throwable { doTest(); }
  public void testMissingReturnWithIf() throws Throwable { doTest(); }
  public void testMissingReturnWithAssertion() throws Throwable { doTest(); }
  public void testMissingReturnThrowException() throws Throwable { doTest(); }
  public void testMissingReturnTryCatch() throws Throwable { doTest(); }
  public void testMissingReturnLastNull() throws Throwable { doTest(); }
  public void testMissingReturnImplicitReturns() throws Throwable {doTest();}
  public void testMissingReturnOvertReturnType() throws Throwable {doTest();}
  public void testMissingReturnFromClosure() throws Throwable {doTest();}
  public void testReturnsWithoutValue() throws Throwable {doTest();}
  public void testEndlessLoop() throws Throwable {doTest();}
  public void testEndlessLoop2() throws Throwable {doTest();}
  public void testExceptionWithFinally() throws Throwable {doTest();}
  public void testOnlyAssert() throws Throwable {doTest();}
  public void testImplicitReturnNull() throws Throwable {doTest();}
  public void testMissingReturnInClosure() {doTest();}
  public void testFinally() {doTest();}
  public void testClosureWithExplicitExpectedType() {doTest()}


  public void testInterruptFlowInElseBranch() {
    doTextText('''\
//correct
public int foo(int bar) {
    if (bar < 0) {
        return -1
    }
    else if (bar > 0) {
        return 12
    }
    else {
        throw new IllegalArgumentException('bar cannot be zero!')
    }
}

//incorrect
public int foo(int bar) {
    if (bar < 0) {
        return -1
    }
    else if (bar > 0) {
        return 12
    }
}

''')
  }

  void doTextText(String text) {
    myFixture.configureByText('___.groovy', text)
    myFixture.testHighlighting(true, false, false)
  }

  private void doTest() {
    myFixture.enableInspections(new MissingReturnInspection());
    myFixture.testHighlighting(true, false, false, getTestName(false) + ".groovy");
  }

}
