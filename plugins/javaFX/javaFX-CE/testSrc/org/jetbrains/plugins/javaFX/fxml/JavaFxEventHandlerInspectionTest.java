package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.openapi.application.PluginPathManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.codeInsight.inspections.JavaFxEventHandlerInspection;

/**
 * @author Pavel.Dolgov
 */
public class JavaFxEventHandlerInspectionTest extends AbstractJavaFXTestCase {

  @Override
  protected void enableInspections() {
    myFixture.enableInspections(new JavaFxEventHandlerInspection());
  }


  public void testHighlightExact() throws Exception {
    doHighlightingTest();
  }

  public void testHighlightNonVoid() throws Exception {
    final JavaFxEventHandlerInspection inspection = new JavaFxEventHandlerInspection();
    inspection.myDetectNonVoidReturnType = true;
    myFixture.enableInspections(inspection);
    doHighlightingTest();
  }

  public void testHighlightAmbiguous() throws Exception {
    doHighlightingTest();
  }

  public void testHighlightSimpleGeneric() throws Exception {
    doHighlightingTest();
  }

  public void testHighlightMixedGeneric() throws Exception {
    doHighlightingTest();
  }

  public void testHighlightWildcard() throws Exception {
    doHighlightingTest();
  }

  private void doHighlightingTest() throws Exception {
    myFixture.configureByFiles(getTestName(true) + ".fxml", getTestName(false) + "Controller.java");
    myFixture.checkHighlighting();
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("javaFX") + "/testData/inspections/eventHandler/";
  }
}
