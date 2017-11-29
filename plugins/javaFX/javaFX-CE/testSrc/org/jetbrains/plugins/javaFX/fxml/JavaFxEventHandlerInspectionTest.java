package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
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


  public void testHighlightExact() {
    doHighlightingTest();
  }

  public void testHighlightNonVoid() {
    final JavaFxEventHandlerInspection inspection = new JavaFxEventHandlerInspection();
    inspection.myDetectNonVoidReturnType = true;
    myFixture.enableInspections(inspection);
    doHighlightingTest();
  }

  public void testHighlightAmbiguous() {
    doHighlightingTest();
  }

  public void testHighlightGeneric() {
    doHighlightingTest();
  }

  public void testHighlightRaw() {
    doHighlightingTest();
  }

  public void testHighlightHalfRaw() {
    doHighlightingTest();
  }

  public void testHighlightSpecific() {
    doHighlightingTest();
  }

  public void testHighlightSuper() {
    doHighlightingTest();
  }

  public void testHighlightWildcard() {
    doHighlightingTest();
  }

  public void testQuickfixRaw() {
    doQuickfixTest("Create method 'onSort'");
  }

  public void testQuickfixHalfRaw() {
    doQuickfixTest("Create method 'onSort'");
  }

  public void testQuickfixSpecific() {
    doQuickfixTest("Create method 'onSort'");
  }

  public void testQuickfixNoField() {
    doQuickfixTest("Create method 'onSort'");
  }

  public void testQuickfixFieldType() {
    doQuickfixTest("Change field 'table' type to 'javafx.scene.control.TableView<java.util.Map<java.lang.String,java.lang.Double>>'");
  }

  public void testQuickfixNoFieldNested() {
    final JavaCodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject()).getCustomSettings(JavaCodeStyleSettings.class);
    final boolean oldImports = settings.INSERT_INNER_CLASS_IMPORTS;
    try {
      settings.INSERT_INNER_CLASS_IMPORTS = true;
      doQuickfixTest("Create method 'onColumnEditStart'");
    }
    finally {
      settings.INSERT_INNER_CLASS_IMPORTS = oldImports;
    }
  }

  public void testQuickfixSuper() {
    doQuickfixTest("Create method 'click'");
  }

  private void doHighlightingTest() {
    myFixture.configureByFiles(getTestName(true) + ".fxml", getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }

  private void doQuickfixTest(final String actionName) {
    String path = getTestName(true) + ".fxml";
    myFixture.configureByFiles(path, getTestName(false) + ".java");
    IntentionAction intention = myFixture.findSingleIntention(actionName);
    assertNotNull(intention);
    myFixture.launchAction(intention);
    myFixture.checkResultByFile(getTestName(false) + ".java", getTestName(false) + "_after.java", true);
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("javaFX") + "/testData/inspections/eventHandler/";
  }
}
