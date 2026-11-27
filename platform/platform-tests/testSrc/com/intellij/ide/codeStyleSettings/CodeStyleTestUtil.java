// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.codeStyleSettings;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.options.SchemeFactory;
import com.intellij.openapi.options.SchemeImportException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.testFramework.core.FileComparisonFailedError;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.CodeStyleSchemeImpl;
import com.intellij.psi.impl.source.codeStyle.CodeStyleSchemeXmlImporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;

/**
 * The helpers of {@link CodeStyleTestCase} for a JUnit 6 test. The test gives the project and the file paths.
 */
@TestOnly
public final class CodeStyleTestUtil {
  private CodeStyleTestUtil() {
  }

  /**
   * Imports the code style scheme of the XML file {@code xmlFilePath} into a new scheme of {@code project}, and returns its settings.
   */
  public static @NotNull CodeStyleSettings importSettings(@NotNull Project project, @NotNull String xmlFilePath) throws SchemeImportException {
    final CodeStyleScheme targetScheme = new CodeStyleSchemeImpl("Test", false, null);
    SchemeFactory<CodeStyleScheme> schemeFactory = new SchemeFactory<>() {
      @Override
      public @NotNull CodeStyleScheme createNewScheme(@Nullable String name) {
        return targetScheme;
      }
    };
    File ioFile = new File(xmlFilePath);
    if (!ioFile.exists()) {
      throw new AssertionError(ioFile + " does not exist");
    }
    VirtualFile vFile = VfsUtil.findFileByIoFile(ioFile, true);
    CodeStyleSchemeXmlImporter importer = new CodeStyleSchemeXmlImporter();
    return importer.importScheme(project, vFile, targetScheme, schemeFactory).getCodeStyleSettings();
  }

  /**
   * Compares {@code actual} with the text of the file {@code expectedFilePath}.
   */
  public static void compareWithExpected(@NotNull String actual, @NotNull String expectedFilePath) throws IOException {
    String expected = FileUtilRt.loadFile(new File(expectedFilePath), true);
    if (!expected.equals(actual)) {
      throw new FileComparisonFailedError("Settings don't match", expected, actual, expectedFilePath);
    }
  }

  /**
   * Returns a scheme named "Test" with new test settings.
   */
  public static @NotNull CodeStyleScheme createTestScheme() {
    return new CodeStyleScheme() {
      private final CodeStyleSettings mySettings = CodeStyle.createTestSettings();

      @Override
      public @NotNull String getName() {
        return "Test";
      }

      @Override
      public boolean isDefault() {
        return false;
      }

      @Override
      public @NotNull CodeStyleSettings getCodeStyleSettings() {
        return mySettings;
      }
    };
  }
}
