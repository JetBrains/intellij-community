// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.LightPlatformTestCase;
import org.editorconfig.configmanagement.extended.EditorConfigCodeStyleSettingsModifier;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.nio.file.Paths;

public abstract class EditorConfigFileSettingsTestCase extends LightPlatformTestCase {
  private CodeStyleSettings myOriginalSettings;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    CodeStyle.dropTemporarySettings(getProject());
    myOriginalSettings = CodeStyle.createTestSettings(CodeStyle.getSettings(getProject()));
    EditorConfigCodeStyleSettingsModifier.setEnabledInTests(true);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      EditorConfigCodeStyleSettingsModifier.setEnabledInTests(false);
      CodeStyle.getSettings(getProject()).copyFrom(myOriginalSettings);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  protected abstract String getRelativePath();

  protected final @NotNull Path getTestDataPath() {
    return Paths.get(PathManagerEx.getHomePath(EditorConfigFileSettingsTestCase.class), getRelativePath(), getTestName(true));
  }

  @NotNull
  protected PsiFile findPsiFile(@NotNull String name) {
    return PsiUtilCore.getPsiFile(getProject(), getVirtualFile(name));
  }

  protected final @NotNull VirtualFile getVirtualFile(@NotNull String name) {
    Path file = getTestDataPath().resolve(name);
    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtil.toSystemIndependentName(file.toString()));
    assertNotNull("Can not find the file" + file, virtualFile);
    return virtualFile;
  }
}
