/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.build;

import com.intellij.compiler.BaseCompilerTestCase;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleImpl;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.LocalFileSystem;
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk;
import org.jetbrains.idea.devkit.projectRoots.Sandbox;

import java.io.File;
import java.util.Arrays;

import static com.intellij.util.io.TestFileSystemBuilder.fs;

/**
 * @author nik
 */
public class PluginModuleCompilationTest extends BaseCompilerTestCase {
  private Sdk myPluginSdk;

  @Override
  protected void setUpJdk() {
    super.setUpJdk();
    new WriteAction() {
      protected void run(final Result result) {
        ProjectJdkTable table = ProjectJdkTable.getInstance();
        myPluginSdk = table.createSdk("IDEA plugin SDK", SdkType.findInstance(IdeaJdk.class));
        SdkModificator modificator = myPluginSdk.getSdkModificator();
        modificator.setSdkAdditionalData(new Sandbox(getSandboxPath(), getTestProjectJdk(), myPluginSdk));
        String rootPath = FileUtil.toSystemIndependentName(PathManager.getJarPathForClass(FileUtilRt.class));
        modificator.addRoot(LocalFileSystem.getInstance().refreshAndFindFileByPath(rootPath), OrderRootType.CLASSES);
        modificator.commitChanges();
        table.addJdk(myPluginSdk);
      }
    }.execute();
  }

  private String getSandboxPath() {
    return getProjectBasePath() + "/sandbox";
  }

  @Override
  protected boolean useExternalCompiler() {
    return true;
  }

  @Override
  protected void tearDown() throws Exception {
    new WriteAction() {
      protected void run(final Result result) {
        ProjectJdkTable.getInstance().removeJdk(myPluginSdk);
      }
    }.execute();

    super.tearDown();
  }

  public void testMakeModule() {
    Module module = setupPluginProject();
    make(module);
    assertOutput(module, fs().dir("xxx").file("MyAction.class"));

    File sandbox = new File(FileUtil.toSystemDependentName(getSandboxPath()));
    assertTrue(sandbox.exists());
    fs().dir("plugins")
          .dir("pluginProject")
            .dir("META-INF").file("plugin.xml").end()
            .dir("classes")
              .dir("xxx").file("MyAction.class")
    .build().assertDirectoryEqual(sandbox);
  }

  public void testRebuild() {
    setupPluginProject();
    CompilationLog log = rebuild();
    assertTrue("Rebuild finished with warnings: " + Arrays.toString(log.getWarnings()), log.getWarnings().length == 0);
  }

  private Module setupPluginProject() {
    copyToProject("plugins/devkit/testData/build/simple");
    Module module = loadModule(getProjectBasePath() + "/pluginProject.iml");
    readJdomExternalizables((ModuleImpl)module);
    return module;
  }
}
