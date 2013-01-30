package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: 1/10/13
 */
public class JavaFXHighlightingTest extends DaemonAnalyzerTestCase {
  @Override
  protected void setUpModule() {
    super.setUpModule();
    PsiTestUtil.addLibrary(getModule(), "javafx", PluginPathManager.getPluginHomePath("javaFX") + "/testData", "jfxrt.jar");
  }

  public void testLoginForm() throws Exception {
    doTest();
  }

  public void testUnresolvedTopLevelController() throws Exception {
    doTest();
  }

  public void testController() throws Exception {
    doTest();
  }

  public void testCoercedTypes() throws Exception {
    doTest();
  }

  public void testStaticProperties() throws Exception {
    doTest();
  }

  public void testEnumValues() throws Exception {
    doTest();
  }
  
  public void testDefaultTagProperties() throws Exception {
    doTest();
  }

  public void testUnresolvedImport() throws Exception {
    doTest();
  }

  public void testImageIcon() throws Exception {
    configureByFiles(null, getTestName(true) + ".fxml", "appIcon.png");
    doDoTest(false, false);
  }

  private void doTest() throws Exception {
    doTest(false, false, getTestName(true) + ".fxml");
  }

  public void testControllerIdRef() throws Exception {
    doTestIdController();
  }

  public void testPackageLocalController() throws Exception {
    configureByFiles(null, getTestName(true) + ".fxml", getTestName(false) + ".java");
    doDoTest(false, false);
  }
  
  private void doTestIdController() throws Exception {
    final String controllerClassName = getTestName(false) + "Controller";
    configureByFiles(null, getTestName(true) + ".fxml", controllerClassName + ".java");
    final PsiClass controllerClass = findClass(controllerClassName);
    assertNotNull(controllerClass);
    assertTrue(controllerClass.getFields().length > 0);
    final int offset = myEditor.getCaretModel().getOffset();
    final PsiReference reference = myFile.findReferenceAt(offset);
    assertNotNull(reference);
    assertEquals(controllerClass.getFields()[0], reference.resolve());
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("javaFX") + "/testData/highlighting/";
  }
}
