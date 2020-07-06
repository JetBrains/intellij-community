// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.actions.DirectoryFormattingOptions;
import com.intellij.codeInsight.actions.ReformatCodeAction;
import com.intellij.codeInsight.actions.TextRangeType;
import com.intellij.formatting.fileSet.NamedScopeDescriptor;
import com.intellij.formatting.fileSet.PatternDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.scope.packageSet.*;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

@SuppressWarnings("SameParameterValue")
public class ExcludedFilesFormatterTest extends FileSetTestCase {

  public static final String UNFORMATTED_SAMPLE = "<a><b></b></a>";
  public static final String FORMATTED_SAMPLE = "<a>\n    <b></b>\n</a>";

  public void testSimpleNoExclusions() throws IOException {
    PsiTestUtil.addContentRoot(myModule, PlatformTestUtil.getOrCreateProjectBaseDir(getProject()));
    VirtualFile f1 = createFile("src/f1.xml", UNFORMATTED_SAMPLE);
    VirtualFile f2 = createFile("src/subdir/f2.xml", UNFORMATTED_SAMPLE);
    formatProjectFiles(false, false);
    assertFormatted(f1);
    assertFormatted(f2);
  }

  public void testPatternExclusions() throws IOException {
    PsiTestUtil.addContentRoot(myModule, PlatformTestUtil.getOrCreateProjectBaseDir(getProject()));
    addPatternExclusions("*2.xml", "test/*");
    VirtualFile f1 = createFile("src/f1.xml", UNFORMATTED_SAMPLE);
    VirtualFile f2 = createFile("src/subdir/f2.xml", UNFORMATTED_SAMPLE);
    VirtualFile f3 = createFile("src/subdir/test/f3.xml", UNFORMATTED_SAMPLE);
    formatProjectFiles(false, false);
    assertFormatted(f1);
    assertUnformatted(f2);
    assertUnformatted(f3);
  }

  public void testProjectScopeExclusions() throws Exception {
    createTestProjectStructure();
    VirtualFile srcRoot = createFile("src/");
    PsiTestUtil.addSourceRoot(ModuleManager.getInstance(getProject()).getModules()[0], srcRoot);
    VirtualFile f1 = createFile("src/f1.xml", UNFORMATTED_SAMPLE);
    VirtualFile f2 = createFile("src/subdir/f2.xml", UNFORMATTED_SAMPLE);
    VirtualFile f3 = createFile("src/subdir/test/f3.xml", UNFORMATTED_SAMPLE);
    NamedScopesHolder localHolder = NamedScopeManager.getInstance(getProject());
    @SuppressWarnings("unused") NamedScope testScope = createScope(localHolder, "testScope", "file:*2.xml");
    CodeStyle.getSettings(getProject()).getExcludedFiles().addDescriptor(new NamedScopeDescriptor("testScope"));
    try {
      formatProjectFiles(false, false);
      assertFormatted(f1);
      assertUnformatted(f2);
      assertFormatted(f3);
    }
    finally {
      localHolder.removeAllSets();
    }
  }

  public void testGlobalScopeExclusions() throws Exception {
    createTestProjectStructure();
    VirtualFile srcRoot = createFile("src/");
    PsiTestUtil.addSourceRoot(ModuleManager.getInstance(getProject()).getModules()[0], srcRoot);
    VirtualFile f1 = createFile("src/f1.xml", UNFORMATTED_SAMPLE);
    VirtualFile f2 = createFile("src/subdir/f2.xml", UNFORMATTED_SAMPLE);
    VirtualFile f3 = createFile("src/subdir/test/f3.xml", UNFORMATTED_SAMPLE);
    NamedScopesHolder appHolder = NamedScopeManager.getInstance(ProjectManager.getInstance().getDefaultProject());
    @SuppressWarnings("unused") NamedScope testScope = createScope(appHolder, "testScope", "file:*2.xml");
    CodeStyle.getSettings(getProject()).getExcludedFiles().addDescriptor(new NamedScopeDescriptor("testScope"));
    try {
      formatProjectFiles(false, false);
      assertFormatted(f1);
      assertUnformatted(f2);
      assertFormatted(f3);
    }
    finally {
      appHolder.removeAllSets();
    }
  }

  public void testNonExistentScope() throws Exception {
    createTestProjectStructure();
    VirtualFile srcRoot = createFile("src/");
    PsiTestUtil.addSourceRoot(ModuleManager.getInstance(getProject()).getModules()[0], srcRoot);
    VirtualFile f1 = createFile("src/f1.xml", UNFORMATTED_SAMPLE);
    VirtualFile f2 = createFile("src/subdir/f2.xml", UNFORMATTED_SAMPLE);
    VirtualFile f3 = createFile("src/subdir/test/f3.xml", UNFORMATTED_SAMPLE);
    NamedScopeDescriptor nonExistentContainer = new NamedScopeDescriptor("nonExistentScope");
    nonExistentContainer.setPattern("file:*2.xml");
    CodeStyle.getSettings(getProject()).getExcludedFiles().addDescriptor(nonExistentContainer);
    formatProjectFiles(false, false);
    assertFormatted(f1);
    assertUnformatted(f2);
    assertFormatted(f3);
  }

  private static NamedScope createScope(@NotNull NamedScopesHolder holder, @NotNull String name, @NotNull String pattern)
    throws ParsingException {
    PackageSet fileSet = PackageSetFactory.getInstance().compile(pattern);
    NamedScope scope = holder.createScope(name, fileSet);
    holder.addScope(scope);
    return scope;
  }

  private void addPatternExclusions(String... patterns) {
    CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    for (String pattern : patterns) {
      settings.getExcludedFiles().addDescriptor(new PatternDescriptor(pattern));
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
    final PsiDirectory psiDirectory = PsiManager.getInstance(myProject).findDirectory(getOrCreateProjectBaseDir());
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
