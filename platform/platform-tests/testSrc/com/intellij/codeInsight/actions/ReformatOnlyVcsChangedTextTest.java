/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.actions;

import com.intellij.lang.LanguageFormatting;
import com.intellij.lang.LanguageImportStatements;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.SimpleContentRevision;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.vcs.MockChangeListManager;
import com.intellij.testFramework.vcs.MockVcsContextFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.MutablePicoContainer;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class ReformatOnlyVcsChangedTextTest extends LightPlatformTestCase {
  private static final String TEMP_DIR_NAME = "dir";
  private PsiDirectory myWorkingDirectory;

  private MockChangeListManager myMockChangeListManager;
  private MockCodeStyleManager myMockCodeStyleManager;
  private MockPlainTextFormattingModelBuilder myMockPlainTextFormattingModelBuilder;
  private MockPlainTextImportOptimizer myMockPlainTextImportOptimizer;

  private ChangeListManager myRealChangeListManager;
  private CodeStyleManager myRealCodeStyleManger;
  private VcsContextFactory myRealVcsContextFactory;

  private final static String COMMITTED =
    "class Test {\n" +
    "          int a      =       22;\n" +
    "        public String getName() { return \"Test\"; }\n" +
    "}";

  private final static String MODIFIED =
    "class Test {\n" +
    "          int a      =       22;\n" +
    "              long l;\n" +
    "              double d;\n" +
    "              int i;\n" +
    "        public String getName() { return \"Test\"; }\n" +
    "            String test1;\n" +
    "            String test2;\n" +
    "}";

  private final static ChangedLines[] CHANGED_LINES = new ChangedLines[] { line(2, 4), line(6, 7) };
  private final static ChangedLines[] NO_CHANGED_LINES = new ChangedLines[0];

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myWorkingDirectory = TestFileStructure.createDirectory(getProject(), getSourceRoot(), TEMP_DIR_NAME);

    myRealChangeListManager = ChangeListManager.getInstance(getProject());
    myMockChangeListManager = new MockChangeListManager();
    registerChangeListManager(myMockChangeListManager);

    myRealCodeStyleManger = CodeStyleManager.getInstance(getProject());
    myMockCodeStyleManager = new MockCodeStyleManager();
    registerCodeStyleManager(myMockCodeStyleManager);

    myRealVcsContextFactory = ServiceManager.getService(VcsContextFactory.class);
    registerVcsContextFactory(new MockVcsContextFactory(getSourceRoot().getFileSystem()));

    myMockPlainTextFormattingModelBuilder = new MockPlainTextFormattingModelBuilder();
    LanguageFormatting.INSTANCE.addExplicitExtension(PlainTextLanguage.INSTANCE, myMockPlainTextFormattingModelBuilder);
    
    myMockPlainTextImportOptimizer = new MockPlainTextImportOptimizer();
    LanguageImportStatements.INSTANCE.addExplicitExtension(PlainTextLanguage.INSTANCE, myMockPlainTextImportOptimizer);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      registerChangeListManager(myRealChangeListManager);
      registerCodeStyleManager(myRealCodeStyleManger);
      registerVcsContextFactory(myRealVcsContextFactory);
      LanguageFormatting.INSTANCE.removeExplicitExtension(PlainTextLanguage.INSTANCE, myMockPlainTextFormattingModelBuilder);
      LanguageImportStatements.INSTANCE.removeExplicitExtension(PlainTextLanguage.INSTANCE, myMockPlainTextImportOptimizer);

      TestFileStructure.delete(myWorkingDirectory.getVirtualFile());
    } finally {
      myRealChangeListManager = null;
      myRealCodeStyleManger = null;
      myRealVcsContextFactory = null;
      myMockChangeListManager = null;
      myMockCodeStyleManager = null;
      myMockPlainTextFormattingModelBuilder = null;
      myMockPlainTextImportOptimizer = null;
      super.tearDown();
    }
  }

  public void testInsertion() throws IOException {
    doTest(
      "public class B {\n" +
      "       int a = 3;\n" +
      "                 String text;\n" +
      "                               Object last = null;\n" +
      "}",

      "public class B {\n" +
      "       int a = 3;\n" +
      "                               int toIndent1 = 1;\n" +
      "                 String text;\n" +
      "                               int toIndent2\n" +
      "                               Object last = null;\n" +
      "}",

      line(2, 2), line(4, 4)
    );
  }

  private static ChangedLines line(int from, int to) {
    return new ChangedLines(from, to);
  }

  public void testDeletion() throws IOException {
    doTest(
      "public class B {\n" +
      "           int a = 3;\n" +
      "           String text;\n" +
      "           Object last = null;\n" +
      "           Object first = null;\n" +
      "           Object second = null;\n" +
      "}",

      "public class B {\n" +
      "           int newInt = 1;\n" +
      "           Object last = null;\n" +
      "}",

      line(1, 1)
    );
  }

  public void testNoReformatOn_DeletionModification() throws IOException {
    doTest(
      "public class B {\n" +
      "           int a = 3;\n" +
      "           String text;\n" +
      "           Object last = null;\n" +
      "           Object first = null;\n" +
      "           Object second = null;\n" +
      "}",

      "public class B {\n" +
      "           int a = 3;\n" +
      "           String text;\n" +
      "           Object last = null;\n" +
      "}"
    );
  }

  public void testModification() throws IOException {
    doTest(
      "public class B {\n" +
      "           int a = 3;\n" +
      "           String text;\n" +
      "           Object last = null;\n" +
      "           Object first = null;\n" +
      "           Object second = null;\n" +
      "}",

      "public class B {\n" +
      "           int a = 33;\n" +
      "           String text;\n" +
      "           Object last = new Object();\n" +
      "           Object first = null;\n" +
      "           Object second = new Object();\n" +
      "}",

      line(1, 1), line(3,3), line(5,5)
    );
  }

  public void testModificationCRLF() throws IOException {
    doTest(
      "public class B {\r\n" +
      "           int a = 3;\r\n" +
      "           String text;\r\n" +
      "           Object last = null;\r\n" +
      "           Object first = null;\r\n" +
      "           Object second = null;\r\n" +
      "}",

      "public class B {\r\n" +
      "           int a = 33;\r\n" +
      "           String text;\r\n" +
      "           Object last = new Object();\r\n" +
      "           Object first = null;\r\n" +
      "           Object second = new Object();\r\n" +
      "}",

      line(1, 1), line(3,3), line(5,5)
    );
  }

  public void testReformatFiles() throws IOException {
    ChangedFilesStructure fs = new ChangedFilesStructure(myWorkingDirectory);

    PsiFile m1 = fs.createFile("Test1.java", COMMITTED, MODIFIED);
    PsiFile u1 = fs.createFile("Test2.java", COMMITTED, COMMITTED);

    fs.createDirectoryAndMakeItCurrent();
    PsiFile m2 = fs.createFile("Test3.java", COMMITTED, MODIFIED);
    PsiFile u2 = fs.createFile("Test4.java", COMMITTED, COMMITTED);

    new ReformatCodeProcessor(getProject(), new PsiFile[] {m1, m2, u1, u2}, null, true).run();

    assertFormattedLines(CHANGED_LINES, m1, m2);
    assertFormattedLines(NO_CHANGED_LINES, u1, u2);
  }

  public void testNoChangesNoFormatting() {
    ChangedFilesStructure fs = new ChangedFilesStructure(myWorkingDirectory);

    PsiFile u1 = fs.createFile("Test1.java", COMMITTED, COMMITTED);
    PsiFile u2 = fs.createFile("Test2.java", COMMITTED, COMMITTED);

    reformatDirectory(fs.getCurrentDirectory());

    assertFormattedLines(NO_CHANGED_LINES, u1, u2);
  }


  public void testReformatOnlyChanged() {
    ChangedFilesStructure fs = new ChangedFilesStructure(myWorkingDirectory);

    PsiFile untouched1 = fs.createFile("Test1.java", COMMITTED, COMMITTED);
    PsiFile untouched2 = fs.createFile("Test2.java", COMMITTED, COMMITTED);

    PsiFile modified1 = fs.createFile("Test4.java", COMMITTED, MODIFIED);
    PsiFile modified2 = fs.createFile("Test5.java", COMMITTED, MODIFIED);
    PsiFile modified3 = fs.createFile("Test6.java", COMMITTED, MODIFIED);

    reformatDirectory(fs.getCurrentDirectory());


    assertFormattedLines(CHANGED_LINES, modified1, modified2, modified3);
    assertFormattedLines(NO_CHANGED_LINES, untouched1, untouched2);
  }

  public void testReformatInAllSubtree() throws IOException {
    ChangedFilesStructure fs = new ChangedFilesStructure(myWorkingDirectory);
    PsiFile modified11 = fs.createFile("Test4.java", COMMITTED, MODIFIED);
    PsiFile modified12 = fs.createFile("Test5.java", COMMITTED, MODIFIED);

    fs.createDirectoryAndMakeItCurrent();
    PsiDirectory dirToReformat = fs.getCurrentDirectory();
    PsiFile modified21 = fs.createFile("Test6.java", COMMITTED, MODIFIED);
    PsiFile modified22 = fs.createFile("Test7.java", COMMITTED, MODIFIED);

    fs.createDirectoryAndMakeItCurrent();
    PsiFile modified31 = fs.createFile("Test8.java", COMMITTED, MODIFIED);
    PsiFile modified32 = fs.createFile("Test9.java", COMMITTED, MODIFIED);

    reformatDirectory(dirToReformat);

    assertFormattedLines(CHANGED_LINES, modified21, modified22, modified31, modified32);
    assertFormattedLines(NO_CHANGED_LINES, modified11, modified12);
  }

  public void testOptimizeImportsInModule() {
    ChangedFilesStructure fs = new ChangedFilesStructure(myWorkingDirectory);

    String initialFile = "initial file";
    PsiFile toModify = fs.createFile("Modified.txt", initialFile, initialFile  + " + unused import");
    PsiFile toModify2 = fs.createFile("Modified2.txt", initialFile, initialFile + " + another unused import");

    PsiFile toKeep = fs.createFile("NonModified.txt", initialFile, initialFile);
    PsiFile toKeep2 = fs.createFile("NonModified2.txt", initialFile, initialFile);

    OptimizeImportsAction optimizeImports = new OptimizeImportsAction();
    OptimizeImportsAction.setProcessVcsChangedFilesInTests(true);
    try {
      optimizeImports.actionPerformed(new TestActionEvent(dataId -> {
        if (CommonDataKeys.PROJECT.is(dataId)) {
          return getProject();
        }
        if (LangDataKeys.MODULE_CONTEXT.is(dataId)) {
          return getModule();
        }
        return null;
      }));
    }
    finally {
      OptimizeImportsAction.setProcessVcsChangedFilesInTests(false);
    }
    
    assertTrue(isImportsOptimized(toModify));
    assertTrue(isImportsOptimized(toModify2));
    assertTrue(!isImportsOptimized(toKeep));
    assertTrue(!isImportsOptimized(toKeep2));
  }

  private static void registerChangeListManager(@NotNull ChangeListManager manager) {
    Project project = getProject();
    assert (project instanceof ComponentManagerImpl);
    ComponentManagerImpl projectComponentManager = (ComponentManagerImpl)project;
    projectComponentManager.registerComponentInstance(ChangeListManager.class, manager);
  }

  private static void registerCodeStyleManager(@NotNull CodeStyleManager manager) {
    String componentKey = CodeStyleManager.class.getName();
    MutablePicoContainer container = (MutablePicoContainer)getProject().getPicoContainer();
    container.unregisterComponent(componentKey);
    container.registerComponentInstance(componentKey, manager);
  }

  private static void registerVcsContextFactory(@NotNull VcsContextFactory factory) {
    String key = VcsContextFactory.class.getName();
    MutablePicoContainer container = (MutablePicoContainer)ApplicationManager.getApplication().getPicoContainer();
    container.unregisterComponent(key);
    container.registerComponentInstance(key, factory);
  }

  private void doTest(@NotNull String committed, @NotNull String modified, @NotNull ChangedLines... lines) {
    ChangedFilesStructure fs = new ChangedFilesStructure(myWorkingDirectory);
    PsiFile file = fs.createFile("Test.java", committed, modified);
    reformatDirectory(myWorkingDirectory);
    assertFormattedRangesEqualsTo(file, lines);
  }

  private void assertFormattedLines(@NotNull ChangedLines[] expectedLines, @NotNull PsiFile... files) {
    for (PsiFile file : files)
      assertFormattedRangesEqualsTo(file, expectedLines);
  }

  private void assertFormattedRangesEqualsTo(@NotNull PsiFile file, ChangedLines... expected) {
    ChangedLines[] formatted = myMockCodeStyleManager.getFormattedLinesFor(file);

    Comparator<ChangedLines> cmp = (o1, o2) -> o1.from < o2.from ? -1 : 1;
    Arrays.sort(expected, cmp);
    Arrays.sort(formatted, cmp);

    assertTrue(getErrorMessage(expected, formatted), Arrays.equals(expected, formatted));
  }
  
  private boolean isImportsOptimized(@NotNull PsiFile file) {
    return myMockPlainTextImportOptimizer.getProcessedFiles().contains(file);
  }

  @NotNull
  private static String getErrorMessage(ChangedLines[] expected, ChangedLines[] actual) {
    return "Expected: " + Arrays.toString(expected) + " Actual: " + Arrays.toString(actual);
  }

  private static void reformatDirectory(@NotNull PsiDirectory dir) {
    ReformatCodeProcessor processor = new ReformatCodeProcessor(getProject(), dir, true, true);
    processor.run();
  }

  class ChangedFilesStructure {
    private TestFileStructure myFileStructure;

    public ChangedFilesStructure(@NotNull PsiDirectory directory) {
      myFileStructure = new TestFileStructure(getModule(), directory);
    }

    public void createDirectoryAndMakeItCurrent() {
      myFileStructure.createDirectoryAndMakeItCurrent("inner");
    }

    @NotNull
    public PsiFile createFile(@NotNull String fileName,
                              @NotNull String committedContent,
                              @NotNull String actualContent) {
      PsiFile file = myFileStructure.addTestFile(fileName, actualContent);
      if (committedContent != actualContent) {
        registerCommittedRevision(committedContent, file);
      }
      return file;
    }

    private void registerCommittedRevision(@NotNull String committedContent, @NotNull PsiFile... files) {
      List<Change> changes = createChanges(committedContent, files);
      injectChanges(changes);
    }

    @NotNull
    private List<Change> createChanges(@NotNull String committed, @NotNull PsiFile... files) {
      List<Change> changes = ContainerUtil.newArrayList();
      for (PsiFile file : files) {
        changes.add(createChange(committed, file));
      }
      return changes;
    }

    private void injectChanges(@NotNull List<Change> changes) {
      Change[] arr = new Change[changes.size()];
      ContainerUtil.toArray(changes, arr);
      myMockChangeListManager.addChanges(arr);
    }

    @NotNull
    private Change createChange(@NotNull String committed, @NotNull PsiFile file) {
      FilePath filePath = VcsUtil.getFilePath(file.getVirtualFile());
      ContentRevision before = new SimpleContentRevision(committed, filePath, "");
      ContentRevision after = new SimpleContentRevision("", filePath, "");
      return new Change(before, after);
    }

    @NotNull
    public PsiDirectory getCurrentDirectory() {
      return myFileStructure.getCurrentDirectory();
    }
  }
}

