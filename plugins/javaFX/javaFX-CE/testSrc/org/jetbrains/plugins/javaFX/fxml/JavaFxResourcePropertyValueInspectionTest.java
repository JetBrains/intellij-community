package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.openapi.application.PluginPathManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.resources.JavaFxResourcePropertyValueInspection;

/**
 * @author Pavel.Dolgov
 */
public class JavaFxResourcePropertyValueInspectionTest extends AbstractJavaFXTestCase {

  public void testResourceKeyInEnumAttribute() {
    myFixture.addFileToProject("messages.properties", "correct.alignment=BASELINE_RIGHT\n" +
                                                      "incorrect.alignment=INCORRECT_VALUE\n");
    doTest();
  }

  public void testResourcePropertySingleFile() {
    myFixture.addFileToProject("messages.properties", "message.width=120\n" +
                                                      "message.height=not a number\n");
    doTest();
  }

  public void testResourcePropertyMuptipleFiles() {
    myFixture.addFileToProject("messages_de.properties", "message.width=120\n" +
                                                         "message.height=keine Zahl\n" +
                                                         "message.alignment=CENTER_LEFT\n");
    myFixture.addFileToProject("messages_fr.properties", "message.width=130\n" +
                                                         "message.height=non un nombre\n" + // invalid value in both resource files
                                                         "message.alignment=valeur non valide\n" + // invalid value in one resource file
                                                         "message.text=le message\n"); // the key exists here, but doesn't in the other file
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
