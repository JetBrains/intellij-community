package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.openapi.application.PluginPathManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.resources.JavaFxResourcePropertyValueInspection;

/**
 * @author Pavel.Dolgov
 */
public class JavaFxResourcePropertyValueInspectionTest extends AbstractJavaFXTestCase {

  public void testResourceKeyInEnumAttribute() {
    myFixture.addFileToProject("messages.properties", """
      correct.alignment=BASELINE_RIGHT
      incorrect.alignment=INCORRECT_VALUE
      """);
    doTest();
  }

  public void testResourcePropertySingleFile() {
    myFixture.addFileToProject("messages.properties", """
      message.width=120
      message.height=not a number
      """);
    doTest();
  }

  public void testResourcePropertyMuptipleFiles() {
    myFixture.addFileToProject("messages_de.properties", """
      message.width=120
      message.height=keine Zahl
      message.alignment=CENTER_LEFT
      """);
    // invalid value in both resource files
    // invalid value in one resource file
    myFixture.addFileToProject("messages_fr.properties", """
      message.width=130
      message.height=non un nombre
      message.alignment=valeur non valide
      message.text=le message
      """); // the key exists here, but doesn't in the other file
    doTest();
  }

  private void doTest() {
    myFixture.testHighlighting(true, false, false, getTestName(true) + ".fxml");
  }

  @Override
  protected void enableInspections() {
    myFixture.enableInspections(new JavaFxResourcePropertyValueInspection());
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("javaFX") + "/testData/inspections/resourcePropertyValue/";
  }
}
