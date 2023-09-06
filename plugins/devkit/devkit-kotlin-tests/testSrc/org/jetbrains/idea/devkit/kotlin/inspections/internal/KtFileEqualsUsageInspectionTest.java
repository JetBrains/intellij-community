package org.jetbrains.idea.devkit.kotlin.inspections.internal;

import org.jetbrains.idea.devkit.inspections.internal.FileEqualsUsageInspectionTestBase;

public class KtFileEqualsUsageInspectionTest extends FileEqualsUsageInspectionTestBase {

  public void testEquals() {
    doTest("equals(java.io.File(\"any\"))", true);
  }

  public void testCompareTo() {
    doTest("compareTo(java.io.File(\"any\"))", true);
  }

  public void testHashCode() {
    doTest("hashCode()", true);
  }

  public void testGetName() {
    doTest("getName()", false);
  }

  @Override
  protected void doTest(String expectedMethodExpression) {
    myFixture.configureByText("Testing.kt",
                              "class Testing {" +
                              "  fun method() {" +
                              "     val file = java.io.File(\"any\");" +
                              "     file." + expectedMethodExpression + ";" +
                              "  }" +
                              "}");
    myFixture.testHighlighting();
  }
}
