/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.history.integration.ui;

import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.history.integration.ui.models.EntireFileHistoryDialogModel;
import com.intellij.history.integration.ui.models.FileHistoryDialogModel;
import com.intellij.history.integration.ui.models.NullRevisionsProgress;
import com.intellij.history.integration.ui.models.RevisionProcessingProgress;
import com.intellij.history.integration.ui.views.FileHistoryDialog;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.text.DateFormatUtil;

import java.util.Date;

public class FileHistoryDialogTest extends LocalHistoryUITestCase {
  public void testDialogWorks() {
    VirtualFile file = createChildData(myRoot, "f.txt");

    FileHistoryDialog d = new FileHistoryDialog(myProject, myGateway, file);
    Disposer.dispose(d);
  }

  public void testTitles() {
    long leftTime = new Date(2001 - 1900, 1, 3, 12, 0).getTime();
    long rightTime = new Date(2002 - 1900, 2, 4, 14, 0).getTime();

    VirtualFile f = createChildData(myRoot, "old.txt");
    setBinaryContent(f, "old".getBytes(), -1, leftTime, this);

    rename(f, "new.txt");
    setBinaryContent(f, "new".getBytes(), -1, rightTime, this);

    byte[] content = new byte[0];
    setBinaryContent(f, content);

    FileHistoryDialogModel m = createFileModelAndSelectRevisions(f, 0, 2);
    assertEquals(FileUtil.toSystemDependentName(f.getPath()), m.getDifferenceModel().getTitle());

    assertEquals(DateFormatUtil.formatPrettyDateTime(leftTime) + " - old.txt",
                 m.getDifferenceModel().getLeftTitle(new NullRevisionsProgress()));
    assertEquals(DateFormatUtil.formatPrettyDateTime(rightTime) + " - new.txt",
                 m.getDifferenceModel().getRightTitle(new NullRevisionsProgress()));
  }

  public void testContent() {
    VirtualFile f = createChildData(myRoot, "f.txt");
    setBinaryContent(f, "old".getBytes());
    setBinaryContent(f, "new".getBytes());
    setBinaryContent(f, "current".getBytes());

    FileHistoryDialogModel m = createFileModelAndSelectRevisions(f, 0, 1);

    assertDiffContents("old", "new", m);
  }

  public void testContentWhenOnlyOneRevisionSelected() {
    VirtualFile f = createChildData(myRoot, "f.txt");
    setBinaryContent(f, "old".getBytes());
    setBinaryContent(f, "new".getBytes());

    FileHistoryDialogModel m = createFileModelAndSelectRevisions(f, 0, 0);

    assertDiffContents("old", "new", m);
  }

  public void testContentForCurrentRevision() {
    VirtualFile f = createChildData(myRoot, "f.txt");
    setBinaryContent(f, "old".getBytes());
    setBinaryContent(f, "current".getBytes());

    FileHistoryDialogModel m = createFileModelAndSelectRevisions(f, 0, 0);

    assertDiffContents("old", "current", m);
    assertTrue(getRightDiffContent(m) instanceof DocumentContent);
  }

  public void testRevertion() throws Exception {
    VirtualFile dir = createChildDirectory(myRoot, "oldDir");
    VirtualFile f = createChildData(dir, "old.txt");
    rename(f, "new.txt");
    rename(dir, "newDir");

    FileHistoryDialogModel m = createFileModelAndSelectRevisions(f, 1, 1);
    m.createReverter().revert();

    assertEquals("old.txt", f.getName());
    assertEquals(f.getParent(), dir);
    assertEquals("newDir", dir.getName());
  }

  private void assertDiffContents(String leftContent, String rightContent, FileHistoryDialogModel m) {
    DiffContent left = getLeftDiffContent(m);
    DiffContent right = getRightDiffContent(m);

    assertContent(leftContent, left);
    assertContent(rightContent, right);
  }

  private DiffContent getLeftDiffContent(FileHistoryDialogModel m) {
    RevisionProcessingProgress p = new NullRevisionsProgress();
    return m.getDifferenceModel().getLeftDiffContent(p);
  }

  private DiffContent getRightDiffContent(FileHistoryDialogModel m) {
    RevisionProcessingProgress p = new NullRevisionsProgress();
    return m.getDifferenceModel().getRightDiffContent(p);
  }

  private FileHistoryDialogModel createFileModel(VirtualFile f) {
    return new EntireFileHistoryDialogModel(myProject, myGateway, getVcs(), f);
  }

  private FileHistoryDialogModel createFileModelAndSelectRevisions(VirtualFile f, int first, int second) {
    FileHistoryDialogModel m = createFileModel(f);
    m.selectRevisions(first, second);
    return m;
  }
}
