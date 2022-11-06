// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.actions;

import com.intellij.lang.LanguageFormatting;
import com.intellij.lang.LanguageImportStatements;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
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
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.vcs.MockChangeListManager;
import com.intellij.testFramework.vcs.MockVcsContextFactory;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;

public class ReformatOnlyVcsChangedTextTest extends LightPlatformTestCase {
  private static final String TEMP_DIR_NAME = "dir";
  private PsiDirectory myWorkingDirectory;

  private MockChangeListManager myMockChangeListManager;
  private MockCodeStyleManager myMockCodeStyleManager;
  private MockPlainTextImportOptimizer myMockPlainTextImportOptimizer;

  private ChangeListManager myRealChangeListManager;
  private CodeStyleManager myRealCodeStyleManger;

  private final static String COMMITTED =
    """
      class Test {
                int a      =       22;
              public String getName() { return "Test"; }
      }""";

  private final static String MODIFIED =
    """
      class Test {
                int a      =       22;
                    long l;
                    double d;
                    int i;
              public String getName() { return "Test"; }
                  String test1;
                  String test2;
      }""";

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

    registerVcsContextFactory(new MockVcsContextFactory(getSourceRoot().getFileSystem()));

    LanguageFormatting.INSTANCE.addExplicitExtension(PlainTextLanguage.INSTANCE, new MockPlainTextFormattingModelBuilder(),
                                                     getTestRootDisposable());

    myMockPlainTextImportOptimizer = new MockPlainTextImportOptimizer();
    LanguageImportStatements.INSTANCE.addExplicitExtension(PlainTextLanguage.INSTANCE, myMockPlainTextImportOptimizer, getTestRootDisposable());
  }

  @Override
  public void tearDown() throws Exception {
    try {
      registerChangeListManager(myRealChangeListManager);
      registerCodeStyleManager(myRealCodeStyleManger);

      TestFileStructure.delete(myWorkingDirectory.getVirtualFile());
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      myRealChangeListManager = null;
      myRealCodeStyleManger = null;
      myMockChangeListManager = null;
      myMockCodeStyleManager = null;
      myMockPlainTextImportOptimizer = null;
      super.tearDown();
    }
  }

  public void testInsertion() {
    doTest(
      """
        public class B {
               int a = 3;
                         String text;
                                       Object last = null;
        }""",

      """
        public class B {
               int a = 3;
                                       int toIndent1 = 1;
                         String text;
                                       int toIndent2
                                       Object last = null;
        }""",

      line(2, 2), line(4, 4)
    );
  }

  private static ChangedLines line(int from, int to) {
    return new ChangedLines(from, to);
  }

  public void testDeletion() {
    doTest(
      """
        public class B {
                   int a = 3;
                   String text;
                   Object last = null;
                   Object first = null;
                   Object second = null;
        }""",

      """
        public class B {
                   int newInt = 1;
                   Object last = null;
        }""",

      line(1, 1)
    );
  }

  public void testNoReformatOn_DeletionModification() {
    doTest(
      """
        public class B {
                   int a = 3;
                   String text;
                   Object last = null;
                   Object first = null;
                   Object second = null;
        }""",

      """
        public class B {
                   int a = 3;
                   String text;
                   Object last = null;
        }"""
    );
  }

  public void testModification() {
    doTest(
      """
        public class B {
                   int a = 3;
                   String text;
                   Object last = null;
                   Object first = null;
                   Object second = null;
        }""",

      """
        public class B {
                   int a = 33;
                   String text;
                   Object last = new Object();
                   Object first = null;
                   Object second = new Object();
        }""",

      line(1, 1), line(3,3), line(5,5)
    );
  }

  public void testModificationCRLF() {
    doTest(
      """
        public class B {\r
                   int a = 3;\r
                   String text;\r
                   Object last = null;\r
                   Object first = null;\r
                   Object second = null;\r
        }""",

      """
        public class B {\r
                   int a = 33;\r
                   String text;\r
                   Object last = new Object();\r
                   Object first = null;\r
                   Object second = new Object();\r
        }""",

      line(1, 1), line(3,3), line(5,5)
    );
  }

  public void testReformatFiles() {
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

  public void testReformatInAllSubtree() {
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
    assertFalse(isImportsOptimized(toKeep));
    assertFalse(isImportsOptimized(toKeep2));
  }

  private void registerChangeListManager(@NotNull ChangeListManager manager) {
    ServiceContainerUtil.replaceService(getProject(), ChangeListManager.class, manager, getTestRootDisposable());
  }

  private void registerCodeStyleManager(@NotNull CodeStyleManager manager) {
    ServiceContainerUtil.replaceService(getProject(), CodeStyleManager.class, manager, getTestRootDisposable());
  }

  private void registerVcsContextFactory(@NotNull VcsContextFactory factory) {
    ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), VcsContextFactory.class, factory, getTestRootDisposable());
  }

  private void doTest(@NotNull String committed, @NotNull String modified, ChangedLines @NotNull ... lines) {
    ChangedFilesStructure fs = new ChangedFilesStructure(myWorkingDirectory);
    PsiFile file = fs.createFile("Test.java", committed, modified);
    reformatDirectory(myWorkingDirectory);
    assertFormattedRangesEqualsTo(file, lines);
  }

  private void assertFormattedLines(ChangedLines @NotNull [] expectedLines, PsiFile @NotNull ... files) {
    for (PsiFile file : files)
      assertFormattedRangesEqualsTo(file, expectedLines);
  }

  private void assertFormattedRangesEqualsTo(@NotNull PsiFile file, ChangedLines... expected) {
    ChangedLines[] formatted = myMockCodeStyleManager.getFormattedLinesFor(file);

    Comparator<ChangedLines> cmp = Comparator.comparingInt(o -> o.from);
    Arrays.sort(expected, cmp);
    Arrays.sort(formatted, cmp);

    assertArrayEquals(getErrorMessage(expected, formatted), expected, formatted);
  }

  private boolean isImportsOptimized(@NotNull PsiFile file) {
    return myMockPlainTextImportOptimizer.getProcessedFiles().contains(file);
  }

  @NotNull
  private static String getErrorMessage(ChangedLines[] expected, ChangedLines[] actual) {
    return "Expected: " + Arrays.toString(expected) + " Actual: " + Arrays.toString(actual);
  }

  private void reformatDirectory(@NotNull PsiDirectory dir) {
    ReformatCodeProcessor processor = new ReformatCodeProcessor(getProject(), dir, true, true);
    processor.run();
  }

  class ChangedFilesStructure {
    private final TestFileStructure myFileStructure;

    ChangedFilesStructure(@NotNull PsiDirectory directory) {
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

    private void registerCommittedRevision(@NotNull String committedContent, PsiFile @NotNull ... files) {
      List<Change> changes = createChanges(committedContent, files);
      injectChanges(changes);
    }

    @NotNull
    private List<Change> createChanges(@NotNull String committed, PsiFile @NotNull ... files) {
      List<Change> changes = new ArrayList<>();
      for (PsiFile file : files) {
        changes.add(createChange(committed, file));
      }
      return changes;
    }

    private void injectChanges(@NotNull List<Change> changes) {
      Change[] arr = changes.toArray(Change.EMPTY_CHANGE_ARRAY);
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