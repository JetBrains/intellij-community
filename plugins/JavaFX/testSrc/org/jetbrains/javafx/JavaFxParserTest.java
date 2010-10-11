package org.jetbrains.javafx;

import com.intellij.testFramework.ParsingTestCase;
import org.jetbrains.javafx.testUtils.JavaFxTestUtil;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxParserTest extends ParsingTestCase {
  public JavaFxParserTest() {
    super("", "fx");
  }

  @Override
  protected String getTestDataPath() {
    return JavaFxTestUtil.getTestDataPath();
  }

  private void doTest() {
    doTest(true);
  }

  public void testHelloWorld() {
    doTest();
  }

  public void testSimpleExpression() {
    doTest();
  }

  public void testDeclarations() {
    doTest();
  }

  public void testImports() {
    doTest();
  }

  public void testModifiers() {
    doTest();
  }

  public void testBreakContinue() {
    doTest();
  }

  public void testForExpression() {
    doTest();
  }

  public void testIfExpression() {
    doTest();
  }

  public void testInsertExpression() {
    doTest();
  }

  public void testThrowExpression() {
    doTest();
  }

  public void testDeleteExpression() {
    doTest();
  }

  public void testTryExpression() {
    doTest();
  }

  public void testFunctionExpression() {
    doTest();
  }

  public void testClass() {
    doTest();
  }

  public void testIfElseExpression() {
    doTest();
  }

  public void testTypeExpression() {
    doTest();
  }

  public void testSequences() {
    doTest();
  }

  public void testBind() {
    doTest();
  }

  public void testBound() {
    doTest();
  }

  public void testTimeLineExpression() {
    doTest();
  }

  public void testObjectLiteral() {
    doTest();
  }

  public void testFunctionType() {
    doTest();
  }

  public void testStaticFunction() {
    doTest();
  }

  public void testObjectLiteral2() {
    doTest();
  }

  public void testSequenceLiteral() {
    doTest();
  }

  public void testParameters() {
    doTest();
  }

  public void testArguments() {
    doTest();
  }

  public void testImplicitConcat() {
    doTest();
  }

  public void testBracesInFormat() {
    doTest();
  }

  public void testThis() {
    doTest();
  }

  public void testOnReplace() {
    doTest();
  }

  public void testOnInvalidate() {
    doTest();
  }

  public void testInvalidate() {
    doTest();
  }

  public void testInvalidateFunction() {
    doTest();
  }

  public void testObjectLiteralBind() {
    doTest();
  }

  public void testQualifiedThis() {
    doTest();
  }
}
