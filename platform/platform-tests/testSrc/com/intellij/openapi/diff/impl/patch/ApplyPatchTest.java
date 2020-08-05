// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.impl.patch.formove.PatchApplier;
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchAction;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestDataFile;
import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@TestDataPath("$CONTENT_ROOT/testData/diff/applyPatch/")
public class ApplyPatchTest extends HeavyPlatformTestCase {
  public void testAddLine() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS, null);
  }

  public void testAddLastLine() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS, null);
  }

  public void testModifyLine() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS, null);
  }

  public void testOverlappingContext() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS, null);
  }

  public void testAddFile() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS, null);
  }

  public void testRemoveFile() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS, null);
  }

  public void testMatchByContext() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS, null);
  }

  public void testMultiFile() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS, null);
  }

  public void testContextDiff() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS, null);
  }

  public void testContextDiffAddLine() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS, null);
  }

  public void testContextDiffRemoveLine() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS, null);
  }

  public void testContextDiffMultiFile() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS, null);
  }

  public void testEmptyLine() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS, null);
  }

  public void testReversedNames() throws Exception {
    doTest(0, ApplyPatchStatus.SUCCESS, null);
  }

  public void testAlreadyApplied() throws Exception {
    doTest(1, ApplyPatchStatus.ALREADY_APPLIED, null);
  }

  public void testPartialApply() throws Exception {
    doTest(1, ApplyPatchStatus.PARTIAL, null);
  }

  public void testContextDiffSingleSpace() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS, null);
  }

  public void testNoNewlineAtEof() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS, null);
  }

  public void testContextNoNewlineAtEof() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS, null);
  }

  public void testRenameFile() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS, null);
  }

  public void testRenameFileGitStyle() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS, null);
  }

  public void testRenameDir() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS, file -> !"empty".equals(file.getNameWithoutExtension()));
  }

  public void testDeleteLastLineWithLineBreak() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS, null);
  }

  public void testDeleteLineContentWithoutLineBreak() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS, null);
  }

  public void testDeleteLastLineWithoutLineBreak() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS, null);
  }

  public void testModifyFileNoHunkAtEOF() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS, null);
  }

  public void testModifyFileRemoveLastEmptyLine() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS, null);
  }

  public void testModifyFileAddLastEmptyLine() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS, null);
  }

  public void testModifyFileLastLine() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS, null);
  }

  public void testModifyFileKeepLastEmptyLine() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS, null);
  }

  public void testMoveFile() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS, null);
  }

  public void testMoveFileWithoutRename() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS, null);
  }

  public void testMoveAndRenameWithNameConflicts() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS, null);
  }

  public void testIncorrectAlreadyAppliedDetection() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS, null);
  }

  public void testOmittedChunkSize() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS, null);
  }

  public void testWrongFileStartUnified() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS, null);
  }

  public void testAddFileWithGitVersion() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS, null);
  }

  public void testModifyLineWithGitVersion() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS, null);
  }

  public void testAddFileWithoutNewlineAtEOF() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS, null);
  }

  public void testFileWithGitStyleCyrillicPaths() throws Exception {
    doTest(0, ApplyPatchStatus.SUCCESS, null);
  }

  public void testFileWithGitStylePathsWithSpaces() throws Exception {
    doTest(0, ApplyPatchStatus.SUCCESS, null);
  }

  private void doTest(final int skipTopDirs, final ApplyPatchStatus expectedStatus, final VirtualFileFilter fileFilter) throws Exception {
    ApplicationManager.getApplication().runWriteAction(() -> {
      FileTypeManager.getInstance().associate(FileTypes.PLAIN_TEXT, new ExtensionFileNameMatcher("old"));
    });

    Path testDataPath = Paths.get(getTestDir(getTestName(true)));
    Path beforePath = testDataPath.resolve("before");
    Path afterPath = testDataPath.resolve("after");
    VirtualFile afterDir = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(afterPath);
    VirtualFile patchedDir = createTestProjectStructure(beforePath.toString());

    List<FilePatch> patches = new ArrayList<>(new PatchReader(testDataPath.resolve("apply.patch")).readTextPatches());

    ApplyPatchAction.applySkipDirs(patches, skipTopDirs);
    PatchApplier patchApplier = new PatchApplier(myProject, patchedDir, patches, null, null);
    ApplyPatchStatus applyStatus = patchApplier.execute(false, false);

    assertEquals(expectedStatus, applyStatus);

    PlatformTestUtil.assertDirectoriesEqual(patchedDir, afterDir, fileFilter);
  }

  @NotNull
  private static String getTestDir(@TestDataFile String dirName) {
    return PlatformTestUtil.getPlatformTestDataPath() + "diff/applyPatch/" + dirName;
  }
}
