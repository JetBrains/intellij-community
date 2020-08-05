// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.JavaProjectTestCase;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public abstract class Eclipse2ModulesTest extends JavaProjectTestCase {
  @NonNls
  private static final String DEPEND_MODULE_NAME = "ws-internals";
  private String myDependantModulePath = "ws-internals";

  protected abstract String getTestPath();

  @Override
  protected void setUpModule() {
    super.setUpModule();

    File testRoot = new File(PluginPathManager.getPluginHomePath("eclipse") + "/testData", getTestPath());
    assertTrue(testRoot.getAbsolutePath(), testRoot.isDirectory());

    File currentTestRoot = new File(testRoot, getTestName(true));
    assertTrue(currentTestRoot.getAbsolutePath(), currentTestRoot.isDirectory());

    getOrCreateProjectBaseDir();
    VirtualFile vTestRoot = LocalFileSystem.getInstance().findFileByIoFile(currentTestRoot);
    copyDirContentsTo(vTestRoot, getOrCreateProjectBaseDir());
  }

  @Override
  protected @NotNull Module createMainModule() {
    return createModule(DEPEND_MODULE_NAME);
  }

  protected void doTest(@NotNull String workspaceRoot, @NotNull String projectRoot) throws Exception {
    VirtualFile baseDir = getOrCreateProjectBaseDir();
    assert baseDir != null;
    final String path = baseDir.getPath() + "/" + workspaceRoot + "/" + myDependantModulePath;
    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
    if (file == null) {
      throw new AssertionError("File " + path + " not found");
    }

    PsiTestUtil.addContentRoot(getModule(), file);
  }

  public void setDependantModulePath(String dependantModulePath) {
    myDependantModulePath = dependantModulePath;
  }
}