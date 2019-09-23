// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.actions;

import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Set;

import static com.intellij.psi.search.GlobalSearchScopesCore.directoryScope;

public class ReformatFilesWithFiltersTest extends LightPlatformTestCase {
  private static final String TEMP_DIR_NAME = "dir";
  private PsiDirectory myWorkingDirectory;

  private MockCodeStyleManager myMockCodeStyleManager;

  private CodeStyleManager myRealCodeStyleManger;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myWorkingDirectory = TestFileStructure.createDirectory(getProject(), getSourceRoot(), TEMP_DIR_NAME);

    myRealCodeStyleManger = CodeStyleManager.getInstance(getProject());
    myMockCodeStyleManager = new MockCodeStyleManager();
    registerCodeStyleManager(myMockCodeStyleManager);

    LanguageFormatting.INSTANCE.addExplicitExtension(PlainTextLanguage.INSTANCE, new MockPlainTextFormattingModelBuilder(),
                                                     getTestRootDisposable());
  }

  @Override
  public void tearDown() throws Exception {
    try {
      if (myRealCodeStyleManger != null) registerCodeStyleManager(myRealCodeStyleManger);
      if (myWorkingDirectory != null) TestFileStructure.delete(myWorkingDirectory.getVirtualFile());
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      myRealCodeStyleManger = null;
      myMockCodeStyleManager = null;
      super.tearDown();
    }
  }

  private void registerCodeStyleManager(@NotNull CodeStyleManager manager) {
    ServiceContainerUtil.replaceService(getProject(), CodeStyleManager.class, manager, getTestRootDisposable());
  }

  public void testReformatWithoutMask() {
    TestFileStructure fileTree = new TestFileStructure(getModule(), myWorkingDirectory);

    PsiFile java1 = fileTree.addTestFile("Test.java", "empty content");
    PsiFile java2 = fileTree.addTestFile("Pair.java", "empty content");
    PsiFile java3 = fileTree.addTestFile("Pair2.java", "empty content");

    PsiFile php = fileTree.addTestFile("Test.php", "empty content");
    PsiFile js = fileTree.addTestFile("Test.js", "empty content");

    reformatDirectoryWithFileMask(myWorkingDirectory, null);
    assertWasFormatted(java1, java2, java3, php, js);
  }

  public void testFormatByOnlyOneMask() {
    TestFileStructure fileTree = new TestFileStructure(getModule(), myWorkingDirectory);

    PsiFile java1 = fileTree.addTestFile("Test.java", "empty content");
    PsiFile java2 = fileTree.addTestFile("Pair.java", "empty content");
    PsiFile java3 = fileTree.addTestFile("Pair2.java", "empty content");

    PsiFile php = fileTree.addTestFile("Test.php", "empty content");
    PsiFile js = fileTree.addTestFile("Test.js", "empty content");

    reformatDirectoryWithFileMask(myWorkingDirectory, "*.java");
    assertWasFormatted(java1, java2, java3);
    assertWasNotFormatted(php, js);

    reformatDirectoryWithFileMask(myWorkingDirectory, "*.js");
    assertWasFormatted(js);
    assertWasNotFormatted(java1, java2, java3, php);

  }

  public void testFormatByMultiMask() {
    TestFileStructure fileTree = new TestFileStructure(getModule(), myWorkingDirectory);

    PsiFile java1 = fileTree.addTestFile("Test.java", "empty content");
    PsiFile java2 = fileTree.addTestFile("Pair.java", "empty content");
    PsiFile java3 = fileTree.addTestFile("Pair2.java", "empty content");

    PsiFile php1 = fileTree.addTestFile("Test.php", "empty content");
    PsiFile php2 = fileTree.addTestFile("Test2.php", "empty content");

    PsiFile js1 = fileTree.addTestFile("Test1.js", "empty content");
    PsiFile js2 = fileTree.addTestFile("Test2.js", "empty content");
    PsiFile js3 = fileTree.addTestFile("Test3.js", "empty content");

    PsiFile py1 = fileTree.addTestFile("Test1.py", "empty content");
    PsiFile py2 = fileTree.addTestFile("Test2.py", "empty content");
    PsiFile py3 = fileTree.addTestFile("Test3.py", "empty content");

    reformatDirectoryWithFileMask(myWorkingDirectory, "*.js, *.java");
    assertWasFormatted(js1, js2, js3, java1, java2, java3);
    assertWasNotFormatted(php1, php2, py1, py2, py3);

    reformatDirectoryWithFileMask(myWorkingDirectory, "*.php, *.js");
    assertWasFormatted(js1, js2, js3, php1, php2);
    assertWasNotFormatted(java1, java2, java3, py1, py2, py3);

    reformatDirectoryWithFileMask(myWorkingDirectory, "*.js, *.php, *.java");
    assertWasFormatted(js1, js2, js3, php1, php2, java1, java2, java3);
    assertWasNotFormatted(py1, py2, py3);

    reformatDirectoryWithFileMask(myWorkingDirectory, "*.js, *.php, *.java, *.py");
    assertWasFormatted(js1, js2, js3, php1, php2, java1, java2, java3, py1, py2, py3);

    reformatDirectoryWithFileMask(myWorkingDirectory, "*.jsp, *.dart");
    assertWasNotFormatted(js1, js2, js3, php1, php2, java1, java2, java3, py1, py2, py3);
  }

  public void testDirectoryScope() {
    TestFileStructure fileTree = new TestFileStructure(getModule(), myWorkingDirectory);
    PsiFile java1 = fileTree.addTestFile("Test1.java", "empty content");
    PsiFile php1 = fileTree.addTestFile("Pair1.php", "empty content");
    PsiFile js1 = fileTree.addTestFile("Pair1.js", "empty content");

    PsiDirectory outer = fileTree.createDirectoryAndMakeItCurrent("toFormat");
    PsiFile java2 = fileTree.addTestFile("Test2.java", "empty content");
    PsiFile php2 = fileTree.addTestFile("Pair2.php", "empty content");
    PsiFile js2 = fileTree.addTestFile("Pair2.js", "empty content");

    PsiDirectory inner = fileTree.createDirectoryAndMakeItCurrent("toFormat");
    PsiFile java3 = fileTree.addTestFile("Test3.java", "empty content");
    PsiFile php3 = fileTree.addTestFile("Pair3.php", "empty content");
    PsiFile js3 = fileTree.addTestFile("Pair3.js", "empty content");


    reformatDirectoryWithScopeFilter(myWorkingDirectory, directoryScope(outer, true));
    assertWasFormatted(java2, php2, js2, java3, php3, js3);
    assertWasNotFormatted(java1, php1, js1);


    reformatDirectoryWithScopeFilter(myWorkingDirectory, directoryScope(outer, false));
    assertWasFormatted(java2, php2, js2);
    assertWasNotFormatted(java1, php1, js1, java3, php3, js3);


    reformatDirectoryWithScopeFilter(myWorkingDirectory, directoryScope(inner, true));
    assertWasFormatted(java3, php3, js3);
    assertWasNotFormatted(java1, php1, js1, java2, php2, js2);

    reformatDirectoryWithScopeFilter(myWorkingDirectory, directoryScope(myWorkingDirectory, false).union(directoryScope(inner, false)));
    assertWasFormatted(java3, php3, js3, java1, php1, js1);
    assertWasNotFormatted(java2, php2, js2);
  }

  public void testDirectoryScopeWithMask() {
    TestFileStructure fileTree = new TestFileStructure(getModule(), myWorkingDirectory);
    PsiFile java1 = fileTree.addTestFile("Test1.java", "empty content");
    PsiFile php1 = fileTree.addTestFile("Pair1.php", "empty content");
    PsiFile js1 = fileTree.addTestFile("Pair1.js", "empty content");

    PsiDirectory outer = fileTree.createDirectoryAndMakeItCurrent("toFormat");
    PsiFile java2 = fileTree.addTestFile("Test2.java", "empty content");
    PsiFile php2 = fileTree.addTestFile("Pair2.php", "empty content");
    PsiFile js2 = fileTree.addTestFile("Pair2.js", "empty content");

    PsiDirectory inner = fileTree.createDirectoryAndMakeItCurrent("toFormat");
    PsiFile java3 = fileTree.addTestFile("Test3.java", "empty content");
    PsiFile php3 = fileTree.addTestFile("Pair3.php", "empty content");
    PsiFile js3 = fileTree.addTestFile("Pair3.js", "empty content");


    reformatDirectory(myWorkingDirectory, "*.js", directoryScope(outer, true));
    assertWasFormatted(js2, js3);
    assertWasNotFormatted(java1, php1, js1, java2, php2, java3, php3);

    reformatDirectory(myWorkingDirectory, "*.js", directoryScope(myWorkingDirectory, false));
    assertWasFormatted(js1);
    assertWasNotFormatted(js2, js3, java1, php1, java2, php2, java3, php3);

    reformatDirectory(myWorkingDirectory, "*.java, *.php", directoryScope(myWorkingDirectory, false).union(directoryScope(inner, false)));
    assertWasFormatted(java1, php1, java3, php3);
    assertWasNotFormatted(java2, php2, js1, js2, js3);
  }

  public void testIDEA126830() {
    TestFileStructure fileTree = new TestFileStructure(getModule(), myWorkingDirectory);

    fileTree.createDirectoryAndMakeItCurrent("src");
    PsiFile java2 = fileTree.addTestFile("Test2.tj", "empty content");
    PsiFile php2 = fileTree.addTestFile("Pair2.php", "empty content");
    PsiFile js2 = fileTree.addTestFile("Pair2.js", "empty content");

    PsiDirectory test = fileTree.createDirectoryAndMakeItCurrent("test");
    PsiFile testJava1 = fileTree.addTestFile("testJava1.tj", "empty content");
    PsiFile testPhp1 = fileTree.addTestFile("testPhp1.php", "empty content");
    PsiFile testJs1 = fileTree.addTestFile("testJs1.js", "empty content");

    GlobalSearchScope testScope = directoryScope(test, true);

    reformatAndOptimize(myWorkingDirectory, testScope);
    assertWasFormatted(testJava1, testPhp1, testJs1);
    assertWasNotFormatted(java2, php2, js2);
  }

  public void testRunOptimizeOnDirectoryMustDrillDownAllSourceDirsInsideButIgnoreExcluded() throws IOException {
    Disposable earlyDisposable = Disposer.newDisposable();
    try {
      VirtualFile root = WriteAction.computeAndWait(()-> ModuleRootManager.getInstance(getModule()).getContentRoots()[0].getParent().createChildDirectory(null, "root"));

      PsiTestUtil.addContentRoot(getModule(), root);
      Disposer.register(earlyDisposable, ()->PsiTestUtil.removeContentEntry(getModule(), root));
      assertFalse(ProjectRootManager.getInstance(getProject()).getFileIndex().isInSourceContent(root));
      assertTrue(ProjectRootManager.getInstance(getProject()).getFileIndex().isInContent(root));

      PsiManager psiManager = PsiManager.getInstance(getProject());
      PsiDirectory workDir = psiManager.findDirectory(root);

      VirtualFile src = WriteAction.computeAndWait(() -> root.createChildDirectory(null, "src"));
      PsiTestUtil.addSourceRoot(getModule(), src);
      Disposer.register(earlyDisposable, ()->PsiTestUtil.removeSourceRoot(getModule(), src));

      VirtualFile A = WriteAction.computeAndWait(() -> src.createChildData(null, "A.java"));

      VirtualFile excluded = WriteAction.computeAndWait(() -> src.createChildDirectory(null, "excluded"));
      PsiTestUtil.addExcludedRoot(getModule(), excluded);
      Disposer.register(earlyDisposable, () -> PsiTestUtil.removeExcludedRoot(getModule(), excluded));

      VirtualFile B = WriteAction.computeAndWait(() -> excluded.createChildData(null, "B.java"));

      GlobalSearchScope testScope = directoryScope(workDir, true);

      reformatAndOptimize(workDir, testScope);
      assertWasFormatted(psiManager.findFile(A));
      assertWasNotFormatted(psiManager.findFile(B));
    }
    finally {
      Disposer.dispose(earlyDisposable);
    }
  }

  public void assertWasFormatted(PsiFile... files) {
    final Set<PsiFile> formattedFiles = myMockCodeStyleManager.getFormattedFiles();
    for (PsiFile file : files) {
      assertTrue(file.getName() + " should be formatted", formattedFiles.contains(file));
    }
  }

  public void assertWasNotFormatted(PsiFile... files) {
    final Set<PsiFile> formattedFiles = myMockCodeStyleManager.getFormattedFiles();
    for (PsiFile file : files) {
      assertTrue(file.getName() + " should not be formatted", !formattedFiles.contains(file));
    }
  }

  public void reformatDirectoryWithFileMask(@NotNull PsiDirectory directory, @Nullable String mask) {
    reformatDirectory(directory, mask, null);
  }

  public void reformatDirectoryWithScopeFilter(@NotNull PsiDirectory directory, @Nullable SearchScope scope) {
    reformatDirectory(directory, null, scope);
  }

  public void reformatWithRearrange(@NotNull PsiDirectory directory, @Nullable SearchScope scope) {
    myMockCodeStyleManager.clearFormattedFiles();
    AbstractLayoutCodeProcessor processor = new ReformatCodeProcessor(getProject(), directory, true, false);
    ReformatCodeAction.registerScopeFilter(processor, scope);

    processor = new RearrangeCodeProcessor(processor);
    processor.run();
  }

  private void reformatAndOptimize(@NotNull PsiDirectory workingDirectory, @NotNull GlobalSearchScope scope) {
    myMockCodeStyleManager.clearFormattedFiles();
    AbstractLayoutCodeProcessor processor = new ReformatCodeProcessor(getProject(), workingDirectory, true, false);
    ReformatCodeAction.registerScopeFilter(processor, scope);

    processor = new OptimizeImportsProcessor(processor);
    processor.run();
  }

  public void reformatDirectory(@NotNull PsiDirectory directory, @Nullable String mask, @Nullable SearchScope scope) {
    myMockCodeStyleManager.clearFormattedFiles();

    ReformatCodeProcessor processor = new ReformatCodeProcessor(getProject(), directory, true, false);
    ReformatCodeAction.registerFileMaskFilter(processor, mask);
    ReformatCodeAction.registerScopeFilter(processor, scope);

    processor.run();
  }
}
