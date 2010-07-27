/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * User: anna
 * Date: 28-Nov-2008
 */
package org.jetbrains.idea.eclipse;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;

public class EclipseLibrariesModificationsTest extends EclipseVarsTest {

  private void doTestCreate(String[] classRoots, String[] sourceRoots) throws Exception {
    final Project project = getProject();
    final String path = project.getBaseDir().getPath() + "/test";
    final Module module = EclipseClasspathTest.setUpModule(path, project);
    final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
    final String parentUrl = VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, model.getContentRoots()[0].getParent().getPath());
    final Library library = model.getModuleLibraryTable().createLibrary("created");
    final Library.ModifiableModel libModifiableModel = library.getModifiableModel();
    for (String classRoot : classRoots) {
      libModifiableModel.addRoot(parentUrl + classRoot, OrderRootType.CLASSES);
    }
    for (String sourceRoot : sourceRoots) {
      libModifiableModel.addRoot(parentUrl + sourceRoot, OrderRootType.SOURCES);
    }
    libModifiableModel.commit();
    model.commit();
    EclipseClasspathTest.checkModule(project.getBaseDir().getPath() + "/expected", module);
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
    doTestExisting(new String[]{"/variableidea/test.jar!/"}, new String[]{"/srcvariableidea1/test.jar!/"}, new String[0]);
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

  private void doTestExisting(String[] classRoots, String[] sourceRoots, String[] javadocs) throws Exception {
    final Project project = getProject();
    final String path = project.getBaseDir().getPath() + "/test";
    final Module module = EclipseClasspathTest.setUpModule(path, project);
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
    EclipseClasspathTest.checkModule(project.getBaseDir().getPath() + "/expected", module);
    EclipseEmlTest.checkModule(project.getBaseDir().getPath() + "/expected", module);
  }

  @Override
  protected String getRelativeTestPath() {
    return "modification";
  }
}