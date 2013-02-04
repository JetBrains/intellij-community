package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.analysis.XmlPathReferenceInspection;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.htmlInspections.RequiredAttributesInspection;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.ProjectScope;
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

  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[] {new XmlPathReferenceInspection(), new RequiredAttributesInspection() };
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

  public void testDefaultTagInList() throws Exception {
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

  public void testConstantNavigation() throws Exception {
    doTestNavigation("java.lang.Double", "NEGATIVE_INFINITY");
  }

  public void testEnumNavigation() throws Exception {
    doTestNavigation("javafx.geometry.Pos", "CENTER");
  }

  private void doTestNavigation(String resultClassName, String resultFieldName) throws Exception {
    configureByFiles(null, getTestName(true) + ".fxml");
    final int offset = myEditor.getCaretModel().getOffset();
    final PsiReference reference = myFile.findReferenceAt(offset);
    assertNotNull(reference);
    final PsiClass resultClass = myJavaFacade.findClass(resultClassName, ProjectScope.getLibrariesScope(getProject()));
    assertNotNull("Class " + resultClassName + " not found", resultClass);
    final PsiField resultField = resultClass.findFieldByName(resultFieldName, false);
    assertNotNull(resultField);
    assertEquals(resultField, reference.resolve());
  }

  public void testSourceAttrRecognition() throws Exception {
    doTest();
  }

  public void testReferenceAttributes() throws Exception {
    doTest();
  }

  public void testDefineAttributes() throws Exception {
    doTest();
  }

  public void testDefinedElements() throws Exception {
    doTest();
  }

  public void testPropertyElementsWithAnyAttributes() throws Exception {
    doTest();
  }

  public void testHandlerWithoutController() throws Exception {
    doTest();
  }

  public void testHandlerWithoutPageLanguage() throws Exception {
    doTest();
  }

  public void testIncludeBtn() throws Exception {
    configureByFiles(null, getTestName(true) + ".fxml", "btn.fxml");
    doDoTest(false, false);
  }

  public void testValueOfAcceptance() throws Exception {
    doTest();
  }

  public void testInstantiationAcceptance() throws Exception {
    doTest();
  }

  public void testFQNtagNames() throws Exception {
    doTest();
  }

  public void testRootTag() throws Exception {
    doTest();
  }

  public void testUnresolvedRootTag() throws Exception {
    doTest();
  }

  public void testRootTagWithoutType() throws Exception {
    doTest();
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("javaFX") + "/testData/highlighting/";
  }
}
