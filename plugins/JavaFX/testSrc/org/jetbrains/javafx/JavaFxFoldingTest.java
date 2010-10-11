package org.jetbrains.javafx;

import org.jetbrains.javafx.testUtils.FoldingTestBase;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxFoldingTest extends FoldingTestBase {

  private void doTest() {
    checkFoldingRegions();
  }

  public void testImports() {
    doTest();
  }

  public void testOneImport() {
    doTest();
  }

  public void testObjectLiteral() {
    doTest();
  }

  public void testClassDefinition() {
    doTest();
  }

  public void testComments() {
    doTest();
  }

  public void testFunctionDefinition() {
    doTest();
  }

  public void testVariableDecl() {
    doTest();
  }
}
