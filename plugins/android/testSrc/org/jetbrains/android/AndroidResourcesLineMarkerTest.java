package org.jetbrains.android;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttributeValue;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidResourcesLineMarkerTest extends AndroidTestCase {
  public void test1() {
  }

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
    doJavaFileNavigationTest(1, true, XmlAttributeValue.class);
  }

  public void testJavaFileNavigation2() throws Exception {
    doJavaFileNavigationTest(3, true, XmlAttributeValue.class);
  }

  public void testJavaFileNavigation3() throws Exception {
    doJavaFileNavigationTest(2, true, PsiFile.class);
  }

  public void testJavaFileNavigation4() throws Exception {
    doJavaFileNavigationTest(0, false, null);
  }

  public void testJavaFileNavigation5() throws Exception {
    doJavaFileNavigationTest(1, true, XmlAttributeValue.class);
  }

  public void testJavaFileNavigation6() throws Exception {
    doJavaFileNavigationTest(1, true, XmlAttributeValue.class);
  }

  public void testJavaFileNavigation7() throws Exception {
    doJavaFileNavigationTest(1, true, XmlAttributeValue.class);
  }

  public void testJavaFileNavigation8() throws Exception {
    doJavaFileNavigationTest(1, true, XmlAttributeValue.class);
  }

  public void testJavaFileNavigation9() throws Exception {
    doJavaFileNavigationTest(1, true, XmlAttributeValue.class);
  }

  public void testJavaFileNavigation10() throws Exception {
    doJavaFileNavigationTest(1, true, XmlAttributeValue.class);
  }

  public void testJavaFileNavigation11() throws Exception {
    doJavaFileNavigationTest(1, true, XmlAttributeValue.class);
  }

  public void testJavaFileNavigation12() throws Exception {
    myFixture.copyFileToProject(BASE_PATH + "dist_delims.xml", "res/values/strings12.xml");
    doJavaFileNavigationTest(1, true, XmlAttributeValue.class);
  }

  private void doJavaFileNavigationTest(int expectedTargets,
                                        boolean expectedEnabled,
                                        @Nullable Class<? extends PsiElement> targetElementClass)
    throws IOException {
    copyRJava();
    String path = "src/p1/p2/" + getTestName(false) + ".java";
    doJavaFileNavigationTest(path, path, expectedTargets, expectedEnabled, true, targetElementClass);
  }

  private void doJavaFileNavigationTest(String srcPath, String destPath, int expectedTargets, boolean expectedEnabled,
                                        boolean testGotoDeclaration, Class<? extends PsiElement> targetElementClass) throws IOException {
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + srcPath, destPath);
    myFixture.configureFromExistingVirtualFile(file);

    // test Ctrl+B
    if (testGotoDeclaration) {
      PsiElement[] targets = GotoDeclarationAction.findAllTargetElements(getProject(), myFixture.getEditor(), myFixture.getCaretOffset());
      assertNotNull(targets);
      assertEquals(expectedTargets, targets.length);

      for (PsiElement target : targets) {
        assertInstanceOf(target, targetElementClass);
      }
    }
  }

  private void copyRJava() throws IOException {
    myFixture.copyFileToProject("R.java", "src/p1/p2/R.java");
  }
}
