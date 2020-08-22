// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestDataFile;
import com.intellij.testFramework.TestDataPath;
import com.intellij.util.LineSeparator;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@TestDataPath("$CONTENT_ROOT/testData/diff/patch/")
public class PatchBuilderTest extends LightPlatformTestCase {
  public void testAddFile() throws Exception {
    doTest();
  }

  public void testAddFileForNullProject() throws Exception {
    doTest(null, false);
  }

  public void testAddFileNoNewline() throws Exception {
    doTest();
  }

  public void testAddLine() throws Exception {
    doTest();
  }

  public void testAddLineToEmptyFile() throws Exception {
    doTest();
  }

  public void testAddLineToEmptyFileNoNewline() throws Exception {
    doTest();
  }

  public void testAddNewlineAtEOF() throws Exception {
    doTest();
  }

  public void testDeleteWholeFile() throws Exception {
    doTest();
  }

  public void testDeleteWholeFileNoNewline() throws Exception {
    doTest();
  }

  public void testModifyWithCRLF() throws Exception {
    doTest(getProject(), false, LineSeparator.CRLF.getSeparatorString());
  }

  public void testModifyLine() throws Exception {
    doTest();
  }

  public void testModifyLineNoNewline() throws Exception {
    doTest();
  }

  public void testModifyLineNoNewlineContext() throws Exception {
    doTest();
  }

  public void testModifyNewline1() throws Exception {
    doTest();
  }

  public void testModifyNewline2() throws Exception {
    doTest();
  }

  public void testModifyNewline3() throws Exception {
    doTest();
  }

  public void testModifyNewline4() throws Exception {
    doTest();
  }

  public void testMultipleFiles() throws Exception {
    doTest(getProject(), true);
  }

  public void testOverlappingContext() throws Exception {
    doTest();
  }

  public void testRemoveFile() throws Exception {
    doTest();
  }

  public void testRemoveFileNoNewline() throws Exception {
    doTest();
  }

  public void testRemoveNewlineAtEOF() throws Exception {
    doTest();
  }

  public void testSingleLine() throws Exception {
    doTest();
  }

  public void testUnchangedFile() throws Exception {
    doTest(getProject(), true);
  }

  private void doTest() throws IOException, VcsException {
    doTest(getProject(), false);
  }

  private void doTest(@Nullable Project project, boolean relativePaths) throws IOException, VcsException {
    doTest(project, relativePaths, null);
  }

  private void doTest(@Nullable Project project, boolean relativePaths, @Nullable String forceLSeparator) throws IOException, VcsException {
    Path testDataPath = Paths.get(getTestDir(getTestName(true)));
    assertTrue(Files.isDirectory(testDataPath));
    Path beforePath = testDataPath.resolve("before");
    Path afterPath = testDataPath.resolve("after");

    List<Change> changes = new ArrayList<>();

    Map<String, File> beforeFileMap = new HashMap<>();
    Map<String, File> afterFileMap = new HashMap<>();

    File[] beforeFiles = FileUtil.notNullize(beforePath.toFile().listFiles());
    for (File file : beforeFiles) {
      beforeFileMap.put(file.getName(), file);
    }
    File[] afterFiles = FileUtil.notNullize(afterPath.toFile().listFiles());
    for (File file : afterFiles) {
      afterFileMap.put(file.getName(), file);
    }

    Set<String> files = ContainerUtil.union(beforeFileMap.keySet(), afterFileMap.keySet());
    for (String file : files) {
      File beforeFile = beforeFileMap.get(file);
      File afterFile = afterFileMap.get(file);
      assert beforeFile != null || afterFile != null;

      ContentRevision beforeRevision = createRevision(beforeFile, "before", relativePaths);
      ContentRevision afterRevision = createRevision(afterFile, "after", relativePaths);
      changes.add(new Change(beforeRevision, afterRevision));
    }

    String expected = FileUtil.loadFile(testDataPath.resolve("expected.patch").toFile());

    StringWriter writer = new StringWriter();
    List<FilePatch> patches = IdeaTextPatchBuilder.buildPatch(project, changes, testDataPath, false);
    UnifiedDiffWriter.write(project, patches, writer, forceLSeparator != null ? forceLSeparator : "\n", null);
    String result = writer.toString();
    if (forceLSeparator == null) {
      expected = StringUtil.convertLineSeparators(expected);
      result = StringUtil.convertLineSeparators(result);
    }
    assertEquals(expected, result);
  }

  @NotNull
  private static String getTestDir(@TestDataFile String dirName) {
    return PlatformTestUtil.getPlatformTestDataPath() + "diff/patch/" + dirName;
  }

  @Nullable
  private static MockContentRevision createRevision(@Nullable File file,
                                                    @NotNull String revision,
                                                    boolean relativePaths) {
    if (file == null) return null;
    String path = file.getPath();
    if (relativePaths) {
      path = FileUtil.toSystemIndependentName(path).replace("/before/", "/");
      path = FileUtil.toSystemIndependentName(path).replace("/after/", "/");
    }
    return new MockContentRevision(file, VcsUtil.getFilePath(path, false), revision);
  }

  private static class MockContentRevision implements ContentRevision, VcsRevisionNumber {
    private final File myFile;
    private final FilePath myFilePath;
    private final String myRevisionName;

    MockContentRevision(@NotNull File file, @NotNull FilePath path, @NotNull String revisionName) {
      myFile = file;
      myFilePath = path;
      myRevisionName = revisionName;
    }

    @Override
    @Nullable
    public String getContent() throws VcsException {
      try {
        return FileUtil.loadFile(myFile);
      }
      catch (IOException ex) {
        throw new VcsException(ex);
      }
    }

    @Override
    @NotNull
    public FilePath getFile() {
      return myFilePath;
    }

    @Override
    @NotNull
    public VcsRevisionNumber getRevisionNumber() {
      return this;
    }

    @NotNull
    @Override
    public String asString() {
      return myRevisionName;
    }

    @Override
    public int compareTo(final VcsRevisionNumber o) {
      return 0;
    }
  }
}
