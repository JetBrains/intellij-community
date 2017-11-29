package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.openapi.application.PluginPathManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.codeInsight.inspections.JavaFxColorRgbInspection;

/**
 * @author Pavel.Dolgov
 */
public class JavaFxColorRgbInspectionTest extends AbstractJavaFXTestCase {
  @Override
  protected void enableInspections() {
    myFixture.enableInspections(new JavaFxColorRgbInspection());
  }

  public void testAttributeHighlighting() {
    doTestHighlighting();
  }

  public void testTagHighlighting() {
    doTestHighlighting();
  }

  public void testFxReference() {
    doTestHighlighting();
  }

  private void doTestHighlighting() {
    myFixture.configureByFiles(getTestName(true) + ".fxml");
    myFixture.checkHighlighting();
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("javaFX") + "/testData/inspections/colorRgb/";
  }
}
