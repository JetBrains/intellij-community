package org.jetbrains.android.dom;

import com.android.sdklib.SdkConstants;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.*;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.android.AndroidFindUsagesTest;
import org.jetbrains.android.AndroidResourcesLineMarkerProvider;
import org.jetbrains.android.AndroidResourcesLineMarkerTest;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.Android15TestProfile;
import org.jetbrains.android.sdk.AndroidSdkTestProfile;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidLibraryProjectTest extends UsefulTestCase {
  @NonNls private static final String BASE_PATH = "libModule/";

  private Module myAppModule;
  private AndroidFacet myAppFacet;

  private Module myLibModule;
  private AndroidFacet myLibFacet;

  private final AndroidSdkTestProfile myTestProfile = new Android15TestProfile();

  protected JavaCodeInsightTestFixture myFixture;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    final TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder();
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());

    String androidJarPath = getTestSdkPath() + myTestProfile.getAndroidJarDirPath();

    final JavaModuleFixtureBuilder appModuleBuilder = projectBuilder.addModule(JavaModuleFixtureBuilder.class);
    String appModuleDir = myFixture.getTempDirPath() + "/app";
    new File(appModuleDir).mkdir();
    AndroidTestCase.tuneModule(appModuleBuilder, androidJarPath, AndroidTestCase.getAbsoluteTestDataPath(), appModuleDir);

    final JavaModuleFixtureBuilder libModuleBuilder = projectBuilder.addModule(JavaModuleFixtureBuilder.class);
    String libModuleDir = myFixture.getTempDirPath() + "/lib";
    new File(libModuleDir).mkdir();
    AndroidTestCase.tuneModule(libModuleBuilder, androidJarPath, AndroidTestCase.getAbsoluteTestDataPath(), libModuleDir);

    myFixture.setUp();
    myFixture.setTestDataPath(AndroidTestCase.getAbsoluteTestDataPath());

    myAppModule = appModuleBuilder.getFixture().getModule();
    myLibModule = libModuleBuilder.getFixture().getModule();

    myAppFacet = AndroidTestCase.addAndroidFacet(myAppModule, myTestProfile, getTestSdkPath());
    myLibFacet = AndroidTestCase.addAndroidFacet(myLibModule, myTestProfile, getTestSdkPath());

    final ModifiableRootModel model = ModuleRootManager.getInstance(myAppModule).getModifiableModel();
    model.addModuleOrderEntry(myLibModule);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        model.commit();
      }
    });

    myFixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML, "app/" + SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyFileToProject(BASE_PATH + "LibAndroidManifest.xml", "lib/" + SdkConstants.FN_ANDROID_MANIFEST_XML);

    myFixture.copyDirectoryToProject("res", "app/res");
    myFixture.copyDirectoryToProject(BASE_PATH + "res", "lib/res");
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    myFixture.tearDown();
    myFixture = null;

    myAppModule = null;
    myLibModule = null;

    myAppFacet = null;
    myLibFacet = null;
  }

  private String getTestSdkPath() {
    return AndroidTestCase.getAbsoluteTestDataPath() + '/' + myTestProfile.getSdkDirName();
  }

  public void testHighlighting() {
    String to = "app/res/layout/" + getTestName(true) + ".xml";
    myFixture.copyFileToProject(BASE_PATH + getTestName(true) + ".xml", to);
    myFixture.testHighlighting(to);
  }

  public void testCompletion() {
    String to = "app/res/layout/" + getTestName(true) + ".xml";
    myFixture.copyFileToProject(BASE_PATH + getTestName(true) + ".xml", to);
    myFixture.testCompletion(to, BASE_PATH + getTestName(true) + "_after.xml");
  }

  public void testCompletion1() {
    String to = "app/res/layout/" + getTestName(true) + ".xml";
    myFixture.copyFileToProject(BASE_PATH + getTestName(true) + ".xml", to);
    myFixture.testCompletion(to, BASE_PATH + getTestName(true) + "_after.xml");
  }

  public void testRJavaFileMarkers() throws Exception {
    doTestRJavaFilesMarkers("/app/src/p1/p2/R.java");
  }

  public void testRJavaFileMarkers1() throws Exception {
    boolean temp = myLibFacet.getConfiguration().LIBRARY_PROJECT;
    try {
      myLibFacet.getConfiguration().LIBRARY_PROJECT = true;
      doTestRJavaFilesMarkers("/app/src/p1/p2/lib/R.java");
    }
    finally {
      myLibFacet.getConfiguration().LIBRARY_PROJECT = temp;
    }
  }

  private void doTestRJavaFilesMarkers(String destPath) {
    List<LineMarkerInfo> markers =
      AndroidResourcesLineMarkerTest.collectMarkers(myFixture, BASE_PATH + getTestName(false) + ".java", destPath);
    assertEquals(3, markers.size());
    for (LineMarkerInfo marker : markers) {
      PsiField field = (PsiField)marker.getElement();
      GutterIconNavigationHandler handler = marker.getNavigationHandler();
      assertInstanceOf(handler, AndroidResourcesLineMarkerProvider.MyNavigationHandler.class);
      PsiElement[] targets = ((AndroidResourcesLineMarkerProvider.MyNavigationHandler)handler).getTargets();
      checkTargets(field, targets);
    }
  }

  public void testJavaFileMarkers() throws Exception {
    myFixture.copyFileToProject(BASE_PATH + "RJavaFileMarkers.java", "app/src/p1/p2/R.java");
    List<LineMarkerInfo> markers =
      AndroidResourcesLineMarkerTest.collectMarkers(myFixture, BASE_PATH + getTestName(false) + ".java", "/app/src/p1/p2/Java.java");
    assertEquals(3, markers.size());
    for (LineMarkerInfo marker : markers) {
      PsiReferenceExpression expression = (PsiReferenceExpression)marker.getElement();
      PsiField field = (PsiField)expression.resolve();
      GutterIconNavigationHandler handler = marker.getNavigationHandler();
      assertInstanceOf(handler, AndroidResourcesLineMarkerProvider.MyLazyNavigationHandler.class);
      Computable<PsiElement[]> targetProvider = ((AndroidResourcesLineMarkerProvider.MyLazyNavigationHandler)handler).getTargetProvider();
      PsiElement[] targets = targetProvider.compute();
      checkTargets(field, targets);
    }
  }

  public void testLayoutFileMarkers() throws Exception {
    myFixture.copyFileToProject(BASE_PATH + "RJavaFileMarkers.java", "app/src/p1/p2/R.java");
    myFixture.copyFileToProject(BASE_PATH + "RJavaFileMarkers.java", "app/src/p1/p2/lib/R.java");
    myFixture.copyFileToProject(BASE_PATH + "RJavaFileMarkers.java", "lib/src/p1/p2/lib/R.java");
    List<LineMarkerInfo> markers =
      AndroidResourcesLineMarkerTest.collectMarkers(myFixture, "res/layout/main.xml", "lib/res/layout/main.xml");
    assertEquals(2, markers.size());

    boolean fileMarker = false;

    for (LineMarkerInfo marker : markers) {
      GutterIconNavigationHandler handler = marker.getNavigationHandler();
      PsiElement[] targets;
      if (marker.getElement() instanceof XmlFile) {
        fileMarker = true;
        assertInstanceOf(handler, AndroidResourcesLineMarkerProvider.MyNavigationHandler.class);
        targets = ((AndroidResourcesLineMarkerProvider.MyNavigationHandler)handler).getTargets();
        assertNotNull(targets);
      }
      else {
        assertInstanceOf(handler, AndroidResourcesLineMarkerProvider.MyLazyNavigationHandler.class);
        Computable<PsiElement[]> targetProvider = ((AndroidResourcesLineMarkerProvider.MyLazyNavigationHandler)handler).getTargetProvider();
        targets = targetProvider.compute();
        assertNotNull(targets);
      }
      assertEquals(3, targets.length);
      for (PsiElement target : targets) {
        assertInstanceOf(target, PsiField.class);
      }
    }

    assertTrue("LineMarker for file not found", fileMarker);
  }

  public static void checkTargets(PsiField field, PsiElement[] targets) {
    assertNotNull(targets);
    assertTrue(targets.length > 0);
    assertEquals(field.getName(), 1, targets.length);
  }

  public void testFileResourceFindUsages() throws Throwable {
    doFindUsagesTest("xml", "lib/res/layout/");
  }

  public void testFileResourceFindUsages1() throws Throwable {
    doFindUsagesTest("xml", "app/res/layout/");
  }

  public void testFileResourceFindUsagesFromJava() throws Throwable {
    doFindUsagesTest("java", "app/src/p1/p2/");
  }

  public void testFileResourceFindUsagesFromJava1() throws Throwable {
    boolean temp = myLibFacet.getConfiguration().LIBRARY_PROJECT;
    try {
      myLibFacet.getConfiguration().LIBRARY_PROJECT = true;
      doFindUsagesTest("java", "app/src/p1/p2/lib/");
    }
    finally {
      myLibFacet.getConfiguration().LIBRARY_PROJECT = temp;
    }
  }

  public void testFileResourceFindUsagesFromJava2() throws Throwable {
    doFindUsagesTest("java", "lib/src/p1/p2/lib/");
  }

  public void testValueResourceFindUsages() throws Throwable {
    doFindUsagesTest("xml", "lib/res/layout/");
  }

  public void testValueResourceFindUsages1() throws Throwable {
    doFindUsagesTest("xml", "app/res/layout/");
  }

  private void doFindUsagesTest(String extension, String dir) throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "FindUsagesClass.java", "app/src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "FindUsagesClass1.java", "app/src/p1/p2/lib/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "FindUsagesClass1.java", "lib/src/p1/p2/lib/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "FindUsagesStyles.xml", "app/res/values/styles.xml");
    myFixture.copyFileToProject(BASE_PATH + "FindUsagesStyles.xml", "lib/res/values/styles.xml");
    myFixture.copyFileToProject(BASE_PATH + "picture1.png", "lib/res/drawable/picture1.png");
    myFixture.copyFileToProject(BASE_PATH + "FindUsagesR.java", "app/src/p1/p2/R.java");
    myFixture.copyFileToProject(BASE_PATH + "FindUsagesR1.java", "app/src/p1/p2/lib/R.java");
    myFixture.copyFileToProject(BASE_PATH + "FindUsagesR1.java", "lib/src/p1/p2/lib/R.java");
    Collection<UsageInfo> references = findCodeUsages(getTestName(false) + "." + extension, dir);
    assertEquals(6, references.size());
  }

  private List<UsageInfo> findCodeUsages(String path, String dir) throws Throwable {
    String newFilePath = dir + path;
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + path, newFilePath);
    Collection<UsageInfo> usages = AndroidFindUsagesTest.findUsages(file, myFixture);
    List<UsageInfo> result = new ArrayList<UsageInfo>();
    for (UsageInfo usage : usages) {
      if (!usage.isNonCodeUsage) {
        result.add(usage);
      }
    }
    return result;
  }
}
