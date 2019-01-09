// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.codeStyleSettings;

import com.intellij.editor.EditorColorSchemeTestCase;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.options.SchemeFactory;
import com.intellij.openapi.options.SchemeImportException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.CodeStyleSchemeImpl;
import com.intellij.psi.impl.source.codeStyle.CodeStyleSchemeXmlImporter;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

@SuppressWarnings("SameParameterValue")
public abstract class CodeStyleTestCase extends LightPlatformTestCase {

  protected static final String BASE_PATH = PathManagerEx.getTestDataPath("/../../../platform/platform-tests/testData/codeStyle/");

  public static void assertXmlOutputEquals(String expected, Element root) throws IOException {
    EditorColorSchemeTestCase.assertXmlOutputEquals(expected, root);
  }

  protected static Element createOption(String name, String value) {
    Element optionElement = new Element("option");
    optionElement.setAttribute("name", name);
    optionElement.setAttribute("value", value);
    return optionElement;
  }

  @Nullable
  protected String getTestDir() {
    return null;
  }

  @NotNull
  protected final String getTestDataPath() {
    String testDir = getTestDir();
    return getBasePath() + (testDir != null ? testDir : "") + File.separator;
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return new LightProjectDescriptor() {
      @Override
      public void setUpProject(@NotNull Project project, @NotNull SetupHandler handler) throws Exception {
        setupProject();
        super.setUpProject(project, handler);
      }
    };
  }

  protected void setupProject() throws Exception {}

  @NotNull
  protected CodeStyleSettings importSettings() throws SchemeImportException {
    final CodeStyleScheme targetScheme = new CodeStyleSchemeImpl("Test", false, null);
    SchemeFactory<CodeStyleScheme> schemeFactory = new SchemeFactory<CodeStyleScheme>() {
      @NotNull
      @Override
      public CodeStyleScheme createNewScheme(@Nullable String name) {
        return targetScheme;
      }
    };
    File ioFile = new File(getTestDataPath() + getTestName(true) + ".xml");
    assertExists(ioFile);
    VirtualFile vFile = VfsUtil.findFileByIoFile(ioFile, true);
    CodeStyleSchemeXmlImporter importer = new CodeStyleSchemeXmlImporter();
    return importer.importScheme(getProject(), vFile, targetScheme, schemeFactory).getCodeStyleSettings();
  }

  protected String loadExpected(@NotNull String ext) throws IOException {
    return FileUtilRt
      .loadFile(new File(getBasePath() + File.separator + getTestDir() + File.separator + getTestName(true) + "." + ext), true);
  }

  protected CodeStyleScheme createTestScheme() {
    return new CodeStyleScheme() {
      @NotNull
      @Override
      public String getName() {
        return "Test";
      }

      @Override
      public boolean isDefault() {
        return false;
      }

      @NotNull
      @Override
      public CodeStyleSettings getCodeStyleSettings() {
        return new CodeStyleSettings();
      }
    };
  }

  protected String getBasePath() {
    return BASE_PATH;
  }
}
