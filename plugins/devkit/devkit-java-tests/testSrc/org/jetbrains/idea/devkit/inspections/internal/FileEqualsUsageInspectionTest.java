package org.jetbrains.idea.devkit.inspections.internal;

public class FileEqualsUsageInspectionTest extends FileEqualsUsageInspectionTestBase {

  public void testEquals() {
    doTest("equals(null)", true);
  }

  public void testCompareTo() {
    doTest("compareTo(null)", true);
  }

  public void testHashCode() {
    doTest("hashCode()", true);
  }

  public void testGetName() {
    doTest("getName()", false);
  }

  @Override
  protected void doTest(String expectedMethodExpression) {
    myFixture.configureByText("Testing.java",
                              "public class Testing {" +
                              "  public void method() {" +
                              "     java.io.File file = null;" +
                              "     file." + expectedMethodExpression + ";" +
                              "  }" +
                              "}");
    myFixture.testHighlighting();
  }
}