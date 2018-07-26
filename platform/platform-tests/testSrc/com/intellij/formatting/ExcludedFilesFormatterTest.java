// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.actions.DirectoryFormattingOptions;
import com.intellij.codeInsight.actions.ReformatCodeAction;
import com.intellij.codeInsight.actions.TextRangeType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

@SuppressWarnings("SameParameterValue")
public class ExcludedFilesFormatterTest extends FileSetTestCase {

  public static final String UNFORMATTED_SAMPLE = "<a><b></b></a>";
  public static final String FORMATTED_SAMPLE = "<a>\n    <b></b>\n</a>";

  public void testSimpleNoExclusions() throws IOException {
    VirtualFile f1 = createFile("src/f1.xml", UNFORMATTED_SAMPLE);
    VirtualFile f2 = createFile("src/subdir/f2.xml", UNFORMATTED_SAMPLE);
    formatProjectFiles(false, false);
    assertFormatted(f1);
    assertFormatted(f2);
  }

  public void testExcluded() throws IOException {
    addExclusions("*2.xml", "test/*");
    VirtualFile f1 = createFile("src/f1.xml", UNFORMATTED_SAMPLE);
    VirtualFile f2 = createFile("src/subdir/f2.xml", UNFORMATTED_SAMPLE);
    VirtualFile f3 = createFile("src/subdir/test/f3.xml", UNFORMATTED_SAMPLE);
    formatProjectFiles(false, false);
    assertFormatted(f1);
    assertUnformatted(f2);
    assertUnformatted(f3);
  }

  private void addExclusions(String... fileSpecs) {
    CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    for (String fileSpec : fileSpecs) {
      settings.getExcludedFiles().addDescriptor(fileSpec);
    }
  }

  private static void assertFormatted(@NotNull VirtualFile file) {
    Document document = FileDocumentManager.getInstance().getDocument(file);
    assertEquals(FORMATTED_SAMPLE, document.getText());
  }

  private static void assertUnformatted(@NotNull VirtualFile file) {
    Document document = FileDocumentManager.getInstance().getDocument(file);
    assertEquals(UNFORMATTED_SAMPLE, document.getText());
  }

  private void formatProjectFiles(boolean optimizeImports, boolean rearrangeCode) {
    final PsiDirectory psiDirectory = PsiManager.getInstance(myProject).findDirectory(myProject.getBaseDir());
    ReformatCodeAction.reformatDirectory(
      getProject(),
      psiDirectory,
      new DirectoryFormattingOptions() {
        @Override
        public boolean isIncludeSubdirectories() {
          return true;
        }

        @Nullable
        @Override
        public String getFileTypeMask() {
          return null;
        }

        @Nullable
        @Override
        public SearchScope getSearchScope() {
          return null;
        }

        @Override
        public TextRangeType getTextRangeType() {
          return TextRangeType.WHOLE_FILE;
        }

        @Override
        public boolean isOptimizeImports() {
          return optimizeImports;
        }

        @Override
        public boolean isRearrangeCode() {
          return rearrangeCode;
        }
      }
    );
    FileDocumentManager.getInstance().saveAllDocuments();
  }
}
