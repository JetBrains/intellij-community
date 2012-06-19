package org.jetbrains.android;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;

import java.io.IOException;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidResourcesLineMarkerTest extends AndroidTestCase {
  public void test1() {}

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

  public void testJavaFileNavigation6() throws Exception {
    doJavaFileNavigationTest(1, true);
  }

  public void testJavaFileNavigation7() throws Exception {
    doJavaFileNavigationTest(1, true);
  }

  private void doJavaFileNavigationTest(int expectedTargets, boolean expectedEnabled) throws IOException {
    copyRJava();
    String path = "src/p1/p2/" + getTestName(false) + ".java";
    doJavaFileNavigationTest(path, path, expectedTargets, expectedEnabled, true);
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
  }

  private void copyRJava() throws IOException {
    myFixture.copyFileToProject("R.java", "src/p1/p2/R.java");
  }
}
