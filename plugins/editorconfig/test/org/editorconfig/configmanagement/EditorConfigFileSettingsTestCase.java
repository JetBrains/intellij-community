// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightPlatformTestCase;
import org.editorconfig.Utils;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.intellij.psi.util.PsiUtilCore.getPsiFile;

public abstract class EditorConfigFileSettingsTestCase extends LightPlatformTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Utils.setFullIntellijSettingsSupportEnabledInTest(true);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Utils.setFullIntellijSettingsSupportEnabledInTest(false);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  protected abstract String getRelativePath();

  protected final String getTestDataPath() {
    return PathManagerEx.getHomePath(EditorConfigFileSettingsTestCase.class)
           + "/" + getRelativePath()
           + "/" + getTestName(true);
  }

  @NotNull
  protected PsiFile findPsiFile(@NotNull String name) {
    File file = new File(getTestDataPath() + "/" + name);
    VirtualFile virtualFile = VfsUtil.findFileByIoFile(file, true);
    assertNotNull("Can not find the file" + file.getPath(), virtualFile);
    return getPsiFile(getProject(), virtualFile);
  }
}
