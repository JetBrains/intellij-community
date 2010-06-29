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
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;

import java.util.HashMap;
import java.util.Map;

public class EclipseLibrariesModificationsTest extends EclipseVarsTest {

  private void doTest(String[] classRoots, String[] sourceRoots) throws Exception {
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
    doTest(new String[]{"/variableidea/test.jar!/"}, new String[]{"/srcvariableidea/test.jar!/"});
  }

  public void testCantReplaceWithVariables() throws Exception {
    doTest(new String[]{"/variableidea1/test.jar!/"}, new String[]{"/srcvariableidea/test.jar!/"});
  }

  public void testReplacedWithVariablesNoSrcExistOnDisc() throws Exception {
    doTest(new String[]{"/variableidea/test.jar!/"}, new String[]{"/srcvariableidea/test.jar!/"});
  }

  public void testReplacedWithVariablesCantReplaceSrc() throws Exception {
    doTest(new String[]{"/variableidea/test.jar!/"}, new String[]{"/srcvariableidea1/test.jar!/"});
  }

  public void testReplacedWithVariablesNoSources() throws Exception {
    doTest(new String[]{"/variableidea/test.jar!/"}, new String[]{});
  }

  @Override
  protected String getRelativeTestPath() {
    return "modification";
  }

  enum Bar{
    ONE;
  }
  class Foo {
    void foo(String each) {
      Map map = new HashMap<String, Bar>();
      map.put(new Foo(), Bar.ONE);
    }
  }

}