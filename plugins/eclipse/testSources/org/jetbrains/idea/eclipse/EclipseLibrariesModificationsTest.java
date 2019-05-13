// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.idea.eclipse;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.ArrayUtil;

public class EclipseLibrariesModificationsTest extends EclipseVarsTest {

  private void doTestCreate(final String[] classRoots, final String[] sourceRoots) throws Exception {
    final Project project = getProject();
    final String path = project.getBasePath() + "/test";
    final Module module = EclipseClasspathTest.setUpModule(path, project);
    PsiTestUtil.addLibrary(module, "created", ModuleRootManager.getInstance(module).getContentRoots()[0].getParent().getPath(), classRoots,
                           sourceRoots);

    EclipseClasspathTest.checkModule(project.getBasePath() + "/expected", module);
  }

  public void testReplacedWithVariables() throws Exception {
    doTestCreate(new String[]{"/variableidea/test.jar!/"}, new String[]{"/srcvariableidea/test.jar!/"});
  }

  public void testCantReplaceWithVariables() throws Exception {
    doTestCreate(new String[]{"/variableidea1/test.jar!/"}, new String[]{"/srcvariableidea/test.jar!/"});
  }

  public void testReplacedWithVariablesNoSrcExistOnDisc() throws Exception {
    doTestCreate(new String[]{"/variableidea/test.jar!/"}, new String[]{"/srcvariableidea/test.jar!/"});
  }

  public void testReplacedWithVariablesCantReplaceSrc() throws Exception {
    doTestCreate(new String[]{"/variableidea/test.jar!/"}, new String[]{"/srcvariableidea1/test.jar!/"});
  }

  public void testReplacedWithVariablesNoSources() throws Exception {
    doTestCreate(new String[]{"/variableidea/test.jar!/"}, new String[]{});
  }

  public void testReplacedExistingWithVariablesCantReplaceSrc() throws Exception {
    doTestExisting(new String[]{"/variableidea/test.jar!/"}, new String[]{"/srcvariableidea1/test.jar!/"}, ArrayUtil.EMPTY_STRING_ARRAY);
  }

  public void testReplacedExistingWithMultipleJavadocs() throws Exception {
    doTestExisting(new String[]{"/variableidea/test.jar!/"}, new String[]{},
                   new String[]{"/srcvariableidea1/test.jar!/", "/srcvariableidea11/test.jar!/"});
  }

  public void testLibAddLibSource() throws Exception {
    doTestExisting(new String[]{"/jars/test.jar!/"},
                   new String[]{"/jars/test.jar!/", "/jars/test-2.jar!/"},
                   new String[]{});
  }

  public void testLibAddVarSource() throws Exception {
    doTestExisting(new String[]{"/jars/test.jar!/"},
                   new String[]{"/jars/test.jar!/", "/srcvariableidea/test.jar!/"},
                   new String[]{});
  }

  public void testLibReplaceVarSource() throws Exception {
    doTestExisting(new String[]{"/jars/test.jar!/"},
                   new String[]{"/srcvariableidea/test.jar!/"},
                   new String[]{});
  }

  public void testLibvarAddLibSource() throws Exception {
    doTestExisting(new String[]{"/variableidea/test.jar!/"},
                   new String[]{"/jars/test.jar!/"},
                   new String[]{});
  }

  public void testLibvarAddVarSource() throws Exception {
    doTestExisting(new String[]{"/variableidea/test.jar!/"},
                   new String[]{"/jars/test.jar!/", "/srcvariableidea/test.jar!/"},
                   new String[]{});
  }

  public void testLibvarReplaceLibSource() throws Exception {
    doTestExisting(new String[]{"/variableidea/test.jar!/"},
                   new String[]{"/jars/test.jar!/"},
                   new String[]{});
  }

  public void testLibvarReplaceVarSource() throws Exception {
    doTestExisting(new String[]{"/variableidea/test.jar!/"},
                   new String[]{"/srcvariableidea/test.jar!/"},
                   new String[]{});
  }

  public void testVarAddLibSource() throws Exception {
    doTestExisting(new String[]{"/variableidea/test.jar!/"},
                   new String[]{"/jars/test.jar!/"},
                   new String[]{});
  }

  public void testVarAddJavadoc() throws Exception {
    doTestExisting(new String[]{"/variableidea/test.jar!/"},
                   new String[]{"/variableidea/test.jar!/"},
                   new String[]{"/jars/test.jar!/"});
  }

  public void testVarAddVarSource() throws Exception {
    doTestExisting(new String[]{"/variableidea/test.jar!/"},
                   new String[]{"/variableidea/test.jar!/", "/srcvariableidea/test.jar!/"},
                   new String[]{});
  }

  public void testVarReplaceLibSource() throws Exception {
    doTestExisting(new String[]{"/variableidea/test.jar!/"},
                   new String[]{"/jars/test.jar!/"},
                   new String[]{});
  }

  public void testVarReplaceVarSource() throws Exception {
    doTestExisting(new String[]{"/variableidea/test.jar!/"},
                   new String[]{"/srcvariableidea/test.jar!/"},
                   new String[]{});
  }

  private void doTestExisting(final String[] classRoots, final String[] sourceRoots, final String[] javadocs) throws Exception {
    final Project project = getProject();
    final String path = project.getBasePath() + "/test";
    final Module module = EclipseClasspathTest.setUpModule(path, project);
    ApplicationManager.getApplication().runWriteAction(() -> {
      final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
      final String parentUrl = VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, model.getContentRoots()[0].getParent().getPath());
      final Library library = model.getModuleLibraryTable().getLibraryByName("test.jar");
      final Library.ModifiableModel libModifiableModel = library.getModifiableModel();
      final String[] oldClsRoots = libModifiableModel.getUrls(OrderRootType.CLASSES);
      for (String oldClsRoot : oldClsRoots) {
        libModifiableModel.removeRoot(oldClsRoot, OrderRootType.CLASSES);
      }

      final String[] oldSrcRoots = libModifiableModel.getUrls(OrderRootType.SOURCES);
      for (String oldSrcRoot : oldSrcRoots) {
        libModifiableModel.removeRoot(oldSrcRoot, OrderRootType.SOURCES);
      }

      final String[] oldJdcRoots = libModifiableModel.getUrls(JavadocOrderRootType.getInstance());
      for (String oldJavadocRoot : oldJdcRoots) {
        libModifiableModel.removeRoot(oldJavadocRoot, JavadocOrderRootType.getInstance());
      }
      for (String classRoot : classRoots) {
        libModifiableModel.addRoot(parentUrl + classRoot, OrderRootType.CLASSES);
      }
      for (String sourceRoot : sourceRoots) {
        libModifiableModel.addRoot(parentUrl + sourceRoot, OrderRootType.SOURCES);
      }
      for (String javadocRoot : javadocs) {
        libModifiableModel.addRoot(parentUrl + javadocRoot, JavadocOrderRootType.getInstance());
      }
      libModifiableModel.commit();
      model.commit();
    });

    EclipseClasspathTest.checkModule(project.getBasePath() + "/expected", module);
    EclipseEmlTest.checkModule(project.getBasePath() + "/expected", module);
  }

  @Override
  protected String getRelativeTestPath() {
    return "modification";
  }
}
