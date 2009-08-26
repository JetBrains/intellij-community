/*
 *  Copyright 2000-2007 JetBrains s.r.o.
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.io.File;

/**
 * @author ven
 */
public abstract class GroovyResolveTestCase extends LightCodeInsightFixtureTestCase {
  public static final LightProjectDescriptor WITH_GROOVY = new LightProjectDescriptor() {
    public ModuleType getModuleType() {
      return StdModuleTypes.JAVA;
    }

    public Sdk getSdk() {
      return JavaSdkImpl.getMockJdk15("java 1.5");
    }

    public void configureModule(Module module, ModifiableRootModel model) {
      final Library.ModifiableModel modifiableModel = model.getModuleLibraryTable().createLibrary("GROOVY").getModifiableModel();
      final VirtualFile groovyJar =
        JarFileSystem.getInstance().refreshAndFindFileByPath(TestUtils.getMockJdkHome() + "/jre/lib/groovy-1.0.jar!/");
      modifiableModel.addRoot(groovyJar, OrderRootType.CLASSES);
      modifiableModel.commit();
    }
  };

  @NonNls protected static final String MARKER = "<ref>";

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return WITH_GROOVY;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    if (new File(myFixture.getTestDataPath() + "/" + getTestName(true)).exists()) {
      myFixture.copyDirectoryToProject(getTestName(true), "");
    }
  }

  protected PsiReference configureByFile(@NonNls String filePath) throws Exception{
    filePath = StringUtil.trimStart(filePath, getTestName(true) + "/");
    final VirtualFile vFile = myFixture.getTempDirFixture().getFile(filePath);
    assertNotNull("file " + filePath + " not found", vFile);

    String fileText = StringUtil.convertLineSeparators(VfsUtil.loadText(vFile));

    int offset = fileText.indexOf(MARKER);
    assertTrue(offset >= 0);
    fileText = fileText.substring(0, offset) + fileText.substring(offset + MARKER.length());

    myFixture.configureByText(vFile.getFileType(), fileText);

    PsiReference ref = myFixture.getFile().findReferenceAt(offset);
    assertNotNull(ref);
    return ref;
  }

}
