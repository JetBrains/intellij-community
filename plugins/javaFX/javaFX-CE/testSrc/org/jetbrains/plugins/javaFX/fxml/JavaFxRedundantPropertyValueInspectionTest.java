package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.openapi.application.PluginPathManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.codeInsight.inspections.JavaFxRedundantPropertyValueInspection;

/**
 * @author Pavel.Dolgov
 */
public class JavaFxRedundantPropertyValueInspectionTest extends AbstractJavaFXQuickFixTest {
  @Override
  protected void enableInspections() {
    myFixture.enableInspections(new JavaFxRedundantPropertyValueInspection());
  }

  public void testModifiedAttribute() throws Exception {
    checkQuickFixNotAvailable("alignment");
  }

  public void testImmediateAttribute() throws Exception {
    doLaunchQuickfixTest("alignment");
  }

  public void testInheritedAttribute() throws Exception {
    doLaunchQuickfixTest("maxHeight");
  }

  public void testModifiedTag() throws Exception {
    checkQuickFixNotAvailable("alignment");
  }

  public void testImmediateTag() throws Exception {
    doLaunchQuickfixTest("alignment");
  }

  public void testInheritedTag() throws Exception {
    doLaunchQuickfixTest("maxHeight");
  }

  public void testAttributeHighlighting() throws Exception {
    doTestHighlighting();
  }

  public void testTagHighlighting() throws Exception {
    doTestHighlighting();
  }

  private void doTestHighlighting() {
    myFixture.configureByFiles(getTestName(true) + ".fxml");
    myFixture.checkHighlighting();
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("javaFX") + "/testData/inspections/redundantValue/";
  }

  @Override
  protected String getHint(String tagName) {
    if (getTestName(false).endsWith("Attribute")) {
      return "Remove attribute " + tagName;
    }
    return "Remove tag " + tagName;
  }
}
