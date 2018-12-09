// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions;
import com.intellij.testFramework.LightPlatformTestCase;
import org.editorconfig.Utils;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.intellij.psi.util.PsiUtilCore.getPsiFile;

@SuppressWarnings("SameParameterValue")
public class EditorConfigFileSettingsTest extends LightPlatformTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Utils.setFullSettingsSupportEnabled(true);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Utils.setFullSettingsSupportEnabled(false);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testFileSettings() {
    PsiFile javaFile = findPsiFile("source.java");
    final CodeStyleSettings settings = CodeStyle.getSettings(javaFile);
    final CommonCodeStyleSettings commonJavaSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    final IndentOptions indentOptions = commonJavaSettings.getIndentOptions();
    assertEquals(3, indentOptions.INDENT_SIZE);
    assertEquals(180, commonJavaSettings.RIGHT_MARGIN);
  }

  @NotNull
  private PsiFile findPsiFile(@NotNull String name) {
    File file = new File(getTestDataPath() + "/" + name);
    VirtualFile virtualFile = VfsUtil.findFileByIoFile(file, true);
    assertNotNull("Can not find the file" + file.getPath(), virtualFile);
    return getPsiFile(getProject(), virtualFile);
  }

  private String getTestDataPath() {
    return PathManagerEx.getHomePath(EditorConfigFileSettingsTest.class)
           + "/plugins/editorconfig/testData/org/editorconfig/configmanagement"
           + "/" + getTestName(true);
  }


}
