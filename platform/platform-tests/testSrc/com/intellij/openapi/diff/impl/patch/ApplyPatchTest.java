// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.impl.patch.formove.PatchApplier;
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestDataFile;
import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@TestDataPath("$CONTENT_ROOT/testData/diff/applyPatch/")
public class ApplyPatchTest extends HeavyPlatformTestCase {
  public void testAddLine() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS);
  }

  public void testAddLastLine() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS);
  }

  public void testModifyLine() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS);
  }

  public void testOverlappingContext() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS);
  }

  public void testAddFile() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS);
  }

  public void testRemoveFile() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS);
  }

  public void testMatchByContext() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS);
  }

  public void testMultiFile() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS);
  }

  public void testContextDiff() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS);
  }

  public void testContextDiffAddLine() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS);
  }

  public void testContextDiffRemoveLine() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS);
  }

  public void testContextDiffMultiFile() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS);
  }

  public void testEmptyLine() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS);
  }

  public void testReversedNames() throws Exception {
    doTest(0, ApplyPatchStatus.SUCCESS);
  }

  public void testAlreadyApplied() throws Exception {
    doTest(1, ApplyPatchStatus.ALREADY_APPLIED);
  }

  public void testPartialApply() throws Exception {
    doTest(1, ApplyPatchStatus.PARTIAL);
  }

  public void testContextDiffSingleSpace() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS);
  }

  public void testNoNewlineAtEof() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS);
  }

  public void testContextNoNewlineAtEof() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS);
  }

  public void testRenameFile() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS);
  }

  public void testRenameFileGitStyle() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS);
  }

  public void testRenameFileGitStyleWithWhitespaces() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS);
  }

  public void testRenameDir() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS, file -> !"empty".equals(file.getNameWithoutExtension()));
  }

  public void testDeleteLastLineWithLineBreak() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS);
  }

  public void testDeleteLineContentWithoutLineBreak() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS);
  }

  public void testDeleteLastLineWithoutLineBreak() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS);
  }

  public void testModifyFileNoHunkAtEOF() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS);
  }

  public void testModifyFileRemoveLastEmptyLine() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS);
  }

  public void testModifyFileAddLastEmptyLine() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS);
  }

  public void testModifyFileLastLine() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS);
  }

  public void testModifyFileKeepLastEmptyLine() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS);
  }

  public void testMoveFile() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS);
  }

  public void testMoveFileWithoutRename() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS);
  }

  public void testMoveAndRenameWithNameConflicts() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS);
  }

  public void testIncorrectAlreadyAppliedDetection() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS);
  }

  public void testOmittedChunkSize() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS);
  }

  public void testWrongFileStartUnified() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS);
  }

  public void testAddFileWithGitVersion() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS);
  }

  public void testModifyLineWithGitVersion() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS);
  }

  public void testAddFileWithoutNewlineAtEOF() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS);
  }

  public void testFileWithGitStyleCyrillicPaths() throws Exception {
    doTest(0, ApplyPatchStatus.SUCCESS);
  }

  public void testFileWithGitStylePathsWithSpaces() throws Exception {
    doTest(0, ApplyPatchStatus.SUCCESS);
  }

  public void testAddEmptyFileWithWhitespaces() throws Exception {
    doTest(1, ApplyPatchStatus.SUCCESS);
  }

  private void doTest(int skipTopDirs, @NotNull ApplyPatchStatus expectedStatus) throws Exception {
    doTest(skipTopDirs, expectedStatus, null);
  }

  private void doTest(int skipTopDirs, @NotNull ApplyPatchStatus expectedStatus, @Nullable VirtualFileFilter fileFilter) throws Exception {
    ApplicationManager.getApplication().runWriteAction(() -> {
      FileTypeManager.getInstance().associate(FileTypes.PLAIN_TEXT, new ExtensionFileNameMatcher("old"));
    });

    VirtualFile testDir = createTestProjectStructure(getTestDir(getTestName(true)));

    Path patchPath = testDir.findChild("apply.patch").toNioPath();
    List<FilePatch> patches = new ArrayList<>(new PatchReader(patchPath).readTextPatches());

    ApplyPatchAction.applySkipDirs(patches, skipTopDirs);
    VirtualFile beforeDir = testDir.findChild("before");
    PatchApplier patchApplier = new PatchApplier(myProject, beforeDir, patches, null, null);
    ApplyPatchStatus applyStatus = patchApplier.execute(false, false);

    assertEquals(expectedStatus, applyStatus);

    VirtualFile afterDir = testDir.findChild("after");
    PlatformTestUtil.assertDirectoriesEqual(beforeDir, afterDir, fileFilter);
  }

  @NotNull
  private static String getTestDir(@TestDataFile String dirName) {
    return PlatformTestUtil.getPlatformTestDataPath() + "diff/applyPatch/" + dirName;
  }
}
