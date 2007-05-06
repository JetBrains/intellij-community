package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.testFramework.ResolveTestCase;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiClass;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.jetbrains.plugins.groovy.GroovyLoader;

/**
 * @author ven
 */
public class ResolveClassTest extends ResolveTestCase {
  protected String getTestDataPath() {
    return TestUtils.getTestDataPath() + "/resolve/class/";
  }

  public void testSamePackage() throws Exception {
    doTest("samePackage/B.groovy");
  }

  public void testImplicitImport() throws Exception {
    doTest("implicitImport/B.groovy");
  }

  public void testOnDemandImport() throws Exception {
    doTest("onDemandImport/B.groovy");
  }

  public void testSimpleImport() throws Exception {
    doTest("simpleImport/B.groovy");
  }

  public void testQualifiedName() throws Exception {
    doTest("qualifiedName/B.groovy");
  }

  public void testImportAlias() throws Exception {
    doTest("qualifiedName/B.groovy");
  }

  private void doTest(String fileName) throws Exception {
    PsiReference ref = configureByFile(fileName);
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiClass);
  }

  protected void setUp() throws Exception {
    super.setUp();

    final ModifiableRootModel rootModel = ModuleRootManager.getInstance(getModule()).getModifiableModel();
    VirtualFile root = LocalFileSystem.getInstance().findFileByPath(getTestDataPath());
    assertNotNull(root);
    ContentEntry contentEntry = rootModel.addContentEntry(root);
    //rootModel.setJdk(JavaSdkImpl.getMockJdk15(""));
    String testName = getTestName(true);
    VirtualFile sourceRoot = root.findChild(testName);
    assertNotNull(sourceRoot);
    contentEntry.addSourceFolder(sourceRoot, false);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        rootModel.commit();
      }
    });
  }
}
