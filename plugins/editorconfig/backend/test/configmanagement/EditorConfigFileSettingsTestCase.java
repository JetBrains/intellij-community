// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.editorconfig.Utils;
import org.editorconfig.configmanagement.extended.EditorConfigCodeStyleSettingsModifier;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.nio.file.Paths;

public abstract class EditorConfigFileSettingsTestCase extends LightPlatformTestCase {
  private CodeStyleSettings myOriginalSettings;
  private Path contentDir;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    CodeStyle.dropTemporarySettings(getProject());
    myOriginalSettings = CodeStyle.createTestSettings(CodeStyle.getSettings(getProject()));
    EditorConfigCodeStyleSettingsModifier.Handler.INSTANCE.setEnabledInTests(true);
    Utils.setEnabledInTests(true);

    // move test data to temp dir to ensure that IJ Project .editorConfig files don't affect tests
    Path testDataDir = Paths.get(PathManagerEx.getHomePath(EditorConfigFileSettingsTestCase.class), getRelativePath(), getTestName(true));
    // no need to delete temp dir - global temp dir is created for each test and deleted after test execution
    contentDir = Paths.get(FileUtil.getTempDirectory(), FileUtil.sanitizeFileName(getTestName(true), false));
    FileUtil.copyDir(testDataDir.toFile(), contentDir.toFile());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Utils.setEnabledInTests(false);
      EditorConfigCodeStyleSettingsModifier.Handler.INSTANCE.setEnabledInTests(false);
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
    return contentDir;
  }

  @NotNull
  protected PsiFile findPsiFile(@NotNull String name) {
    return PsiUtilCore.getPsiFile(getProject(), getVirtualFile(name));
  }

  protected final @NotNull VirtualFile getVirtualFile(@NotNull String name) {
    Path file = getTestDataPath().resolve(name);
    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file);
    assertNotNull("Can not find the file" + file, virtualFile);
    return virtualFile;
  }
}
