// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.idea.eclipse;

import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.JavaProjectTestCase;
import org.jetbrains.annotations.NonNls;

import java.io.File;

public abstract class EclipseVarsTest extends JavaProjectTestCase {
  @NonNls private static final String VARIABLE = "variable";
  @NonNls private static final String SRCVARIABLE = "srcvariable";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final File testRoot = new File(PluginPathManager.getPluginHomePath("eclipse") + "/testData", getRelativeTestPath());
    assertTrue(testRoot.getAbsolutePath(), testRoot.isDirectory());

    final File currentTestRoot = new File(testRoot, getTestName(true));
    assertTrue(currentTestRoot.getAbsolutePath(), currentTestRoot.isDirectory());

    final String tempPath = getProject().getBasePath();
    final File tempDir = new File(tempPath);
    VirtualFile vTestRoot = LocalFileSystem.getInstance().findFileByIoFile(currentTestRoot);
    copyDirContentsTo(vTestRoot, getOrCreateProjectBaseDir());

    final VirtualFile virtualTestDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDir);
    assertNotNull(tempDir.getAbsolutePath(), virtualTestDir);
    virtualTestDir.refresh(false, true);

    PathMacros.getInstance().setMacro(VARIABLE, new File(tempPath, VARIABLE + "idea").getPath());
    PathMacros.getInstance().setMacro(SRCVARIABLE, new File(tempPath, SRCVARIABLE + "idea").getPath());
  }

  protected abstract String getRelativeTestPath();

  @Override
  protected void tearDown() throws Exception {
    try {
      PathMacros pathMacros = PathMacros.getInstance();
      pathMacros.setMacro(VARIABLE, null);
      pathMacros.setMacro(SRCVARIABLE, null);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }
}