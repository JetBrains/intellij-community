package org.jetbrains.android;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import org.jetbrains.android.actions.GotoResourceAction;

import java.io.IOException;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidResourcesLineMarkerTest extends AndroidTestCase {
  private static final String BASE_PATH = "/resNavigation/";

  public AndroidResourcesLineMarkerTest() {
    super(false);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.copyFileToProject(BASE_PATH + "AndroidManifest.xml", "AndroidManifest.xml");
    myFixture.copyDirectoryToProject(BASE_PATH + "res", "res");
  }

  public void testRJavaFile() throws Exception {
    List<LineMarkerInfo> markers = collectMarkers("src/p1/p2/R.java");
    assertEquals(26, markers.size());
    for (LineMarkerInfo marker : markers) {
      PsiField field = (PsiField)marker.getElement();
      GutterIconNavigationHandler handler = marker.getNavigationHandler();
      assertInstanceOf(handler, AndroidResourcesLineMarkerProvider.MyNavigationHandler.class);
      PsiElement[] targets = ((AndroidResourcesLineMarkerProvider.MyNavigationHandler)handler).getTargets();
      checkTargets(field, targets);
    }
  }

  public void testValueResourcesFile() throws Exception {
    copyRJava();
    List<LineMarkerInfo> markers = collectMarkers("res/values/ids.xml");
    assertEquals(3, markers.size());
    for (LineMarkerInfo marker : markers) {
      GutterIconNavigationHandler handler = marker.getNavigationHandler();
      assertInstanceOf(handler, AndroidResourcesLineMarkerProvider.MyLazyNavigationHandler.class);
      Computable<PsiElement[]> targetProvider = ((AndroidResourcesLineMarkerProvider.MyLazyNavigationHandler)handler).getTargetProvider();
      PsiElement[] targets = targetProvider.compute();
      assertNotNull(targets);
      assertEquals(1, targets.length);
      for (PsiElement target : targets) {
        assertInstanceOf(target, PsiField.class);
      }
    }
  }

  public void testLayoutFile() throws Exception {
    copyRJava();
    List<LineMarkerInfo> markers = collectMarkers("res/layout/layout1.xml");
    assertEquals(5, markers.size());

    boolean fileMarker = false;

    for (LineMarkerInfo marker : markers) {
      GutterIconNavigationHandler handler = marker.getNavigationHandler();
      PsiElement[] targets;
      if (marker.getElement() instanceof XmlFile) {
        fileMarker = true;
        assertInstanceOf(handler, AndroidResourcesLineMarkerProvider.MyNavigationHandler.class);
        targets = ((AndroidResourcesLineMarkerProvider.MyNavigationHandler)handler).getTargets();
      }
      else {
        assertInstanceOf(handler, AndroidResourcesLineMarkerProvider.MyLazyNavigationHandler.class);
        Computable<PsiElement[]> targetProvider = ((AndroidResourcesLineMarkerProvider.MyLazyNavigationHandler)handler).getTargetProvider();
        targets = targetProvider.compute();
      }
      assertNotNull(targets);
      assertEquals(1, targets.length);
      for (PsiElement target : targets) {
        assertInstanceOf(target, PsiField.class);
      }
    }

    assertTrue("LineMarker for file not found", fileMarker);
  }

  public void testJavaFileMarkers() throws Exception {
    copyRJava();
    List<LineMarkerInfo> markers = collectMarkers("src/p1/p2/Java.java");

    // do not draw line markers on usages of a resource: AndroidGotoDeclarationHandler provides navigation instead
    assertEquals(0, markers.size());

    /*for (LineMarkerInfo marker : markers) {
      PsiReferenceExpression expression = (PsiReferenceExpression)marker.getElement();
      PsiField field = (PsiField)expression.resolve();
      GutterIconNavigationHandler handler = marker.getNavigationHandler();
      assertInstanceOf(handler, AndroidResourcesLineMarkerProvider.MyLazyNavigationHandler.class);
      Computable<PsiElement[]> targetProvider = ((AndroidResourcesLineMarkerProvider.MyLazyNavigationHandler)handler).getTargetProvider();
      PsiElement[] targets = targetProvider.compute();
      checkTargets(field, targets);
    }*/
  }

  public void testJavaFileNavigation1() throws Exception {
    doJavaFileNavigationTest(1, true);
  }

  public void testJavaFileNavigation2() throws Exception {
    doJavaFileNavigationTest(3, true);
  }

  public void testJavaFileNavigation3() throws Exception {
    doJavaFileNavigationTest(2, true);
  }

  public void testJavaFileNavigation4() throws Exception {
    doJavaFileNavigationTest(0, false);
  }

  public void testJavaFileNavigation5() throws Exception {
    doJavaFileNavigationTest(1, true);
  }

  public void testRJavaFileNavigation1() throws Exception {
    doRJavaFileNavigationTest(1);
  }

  public void testRJavaFileNavigation2() throws Exception {
    doRJavaFileNavigationTest(2);
  }

  public void testRJavaFileNavigation3() throws Exception {
    doRJavaFileNavigationTest(3);
  }

  public void testRJavaFileNavigation4() throws Exception {
    doRJavaFileNavigationTest(1);
  }

  public void testRJavaFileNavigation5() throws Exception {
    doRJavaFileNavigationTest(0);
  }

  public void testValueResourcesNavigation() throws Exception {
    copyRJava();
    String fileName = getTestName(false) + ".xml";
    doJavaFileNavigationTest(fileName, "res/values/" + fileName, 1, true, false);
  }

  private void doJavaFileNavigationTest(int expectedTargets, boolean expectedEnabled) throws IOException {
    copyRJava();
    String path = "src/p1/p2/" + getTestName(false) + ".java";
    doJavaFileNavigationTest(path, path, expectedTargets, expectedEnabled, true);
  }

  private void doRJavaFileNavigationTest(int expectedTargets) throws IOException {
    doJavaFileNavigationTest("src/p1/p2/" + getTestName(false) + ".java", "src/p1/p2/R.java", expectedTargets, true, false);
  }

  private void doJavaFileNavigationTest(String srcPath, String destPath, int expectedTargets, boolean expectedEnabled,
                                        boolean testGotoDeclaration) throws IOException {
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + srcPath, destPath);
    myFixture.configureFromExistingVirtualFile(file);

    // test Ctrl+B
    if (testGotoDeclaration) {
      PsiElement[] targets = GotoDeclarationAction.findAllTargetElements(getProject(), myFixture.getEditor(), myFixture.getCaretOffset());
      assertNotNull(targets);
      assertEquals(expectedTargets, targets.length);
    }

    // test Ctrl+Alt+Shift+R
    GotoResourceAction action = new GotoResourceAction();
    DataContext dataContext = DataManager.getInstance().getDataContextFromFocus().getResult();
    AnActionEvent event = new AnActionEvent(null, dataContext, "", action.getTemplatePresentation(), ActionManager.getInstance(), 0);
    action.update(event);
    assertEquals(expectedEnabled, event.getPresentation().isEnabled());
    PsiElement[] targets = GotoResourceAction.findTargets(dataContext);
    assertEquals(expectedTargets, targets.length);
  }

  private List<LineMarkerInfo> collectMarkers(String filePath) throws IOException {
    return collectMarkers(myFixture, BASE_PATH + filePath, filePath);
  }

  public static List<LineMarkerInfo> collectMarkers(JavaCodeInsightTestFixture fixture, String fromPath, String filePath) {
    VirtualFile file = fixture.copyFileToProject(fromPath, filePath);
    fixture.configureFromExistingVirtualFile(file);
    fixture.doHighlighting();
    List<LineMarkerInfo> markers = DaemonCodeAnalyzerImpl.getLineMarkers(fixture.getEditor().getDocument(), fixture.getProject());
    assertNotNull(markers);
    return markers;
  }

  private static void checkTargets(PsiField field, PsiElement[] targets) {
    assertNotNull(targets);
    assertTrue(targets.length > 0);
    int expectedTargetCount = getExpectedTargetCount(field.getName());
    assertEquals(field.getName(), expectedTargetCount, targets.length);
  }

  private static int getExpectedTargetCount(String fieldName) {
    if ("png".equals(fieldName)) {
      return 2;
    }
    if ("str2".equals(fieldName)) {
      return 3;
    }
    if ("myId1".equals(fieldName)) {
      return 3;
    }
    return 1;
  }

  private void copyRJava() throws IOException {
    myFixture.copyFileToProject(BASE_PATH + "src/p1/p2/R.java", "src/p1/p2/R.java");
  }
}
