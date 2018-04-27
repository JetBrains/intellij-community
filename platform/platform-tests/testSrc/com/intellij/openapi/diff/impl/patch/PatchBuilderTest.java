/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestDataFile;
import com.intellij.testFramework.TestDataPath;
import com.intellij.util.LineSeparator;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@TestDataPath("$CONTENT_ROOT/testData/diff/patch/")
public class PatchBuilderTest extends PlatformTestCase {
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
    doTest(myProject, false, LineSeparator.CRLF.getSeparatorString());
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
    doTest(myProject, true);
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
    doTest(myProject, true);
  }

  private void doTest() throws IOException, VcsException {
    doTest(myProject, false);
  }

  private void doTest(@Nullable Project project, boolean relativePaths) throws IOException, VcsException {
    doTest(project, relativePaths, null);
  }

  private void doTest(@Nullable Project project, boolean relativePaths, @Nullable String forceLSeparator) throws IOException, VcsException {
    String testDataPath = getTestDir(getTestName(true));
    assertTrue(new File(testDataPath).isDirectory());
    String beforePath = testDataPath + "/before";
    String afterPath = testDataPath + "/after";

    List<Change> changes = new ArrayList<>();

    Map<String, File> beforeFileMap = new HashMap<>();
    Map<String, File> afterFileMap = new HashMap<>();

    File[] beforeFiles = FileUtil.notNullize(new File(beforePath).listFiles());
    for (File file : beforeFiles) {
      beforeFileMap.put(file.getName(), file);
    }
    File[] afterFiles = FileUtil.notNullize(new File(afterPath).listFiles());
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

    String expected = FileUtil.loadFile(new File(testDataPath, "expected.patch"));

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

    public MockContentRevision(@NotNull File file, @NotNull FilePath path, @NotNull String revisionName) {
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
