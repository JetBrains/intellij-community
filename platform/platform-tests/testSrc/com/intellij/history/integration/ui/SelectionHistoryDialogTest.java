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

import com.intellij.diff.actions.DocumentFragmentContent;
import com.intellij.diff.contents.DiffContent;
import com.intellij.history.integration.revertion.Reverter;
import com.intellij.history.integration.ui.models.FileDifferenceModel;
import com.intellij.history.integration.ui.models.NullRevisionsProgress;
import com.intellij.history.integration.ui.models.RevisionProcessingProgress;
import com.intellij.history.integration.ui.views.SelectionHistoryDialog;
import com.intellij.history.integration.ui.views.SelectionHistoryDialogModel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

import static org.easymock.EasyMock.*;

public class SelectionHistoryDialogTest extends LocalHistoryUITestCase {
  private VirtualFile f;
  private FileDifferenceModel dm;
  private SelectionHistoryDialogModel m;

  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();

    f = createChildData(myRoot, "f.txt");
    setBinaryContent(f,"a\nb\nc\n".getBytes(), -1, 123,this);
    setBinaryContent(f,"a\nbc\nc\nd\n".getBytes(), -1, 456,this);
    setBinaryContent(f,"a\nbcd\nc\ne\n".getBytes(), -1, 789,this);
  }

  public void testDialogWorks() {
    SelectionHistoryDialog d = new SelectionHistoryDialog(myProject, myGateway, f, 0, 0);
    Disposer.dispose(d);
  }

  public void testTitles() {
    rename(f, "ff.txt");
    setBinaryContent(f,new byte[0]);

    initModelOnSecondLineAndSelectRevisions(0, 1);

    assertEquals(FileUtil.toSystemDependentName(f.getPath()), dm.getTitle());
    assertTrue(dm.getLeftTitle(new NullRevisionsProgress()), dm.getLeftTitle(new NullRevisionsProgress()).endsWith(" - f.txt"));
    assertTrue(dm.getRightTitle(new NullRevisionsProgress()), dm.getRightTitle(new NullRevisionsProgress()).endsWith(" - ff.txt"));
  }

  public void testCalculationProgress() {
    initModelOnSecondLineAndSelectRevisions(2, 2);

    RevisionProcessingProgress p = createMock(RevisionProcessingProgress.class);
    p.processed(25);
    p.processed(50);
    p.processed(75);
    p.processed(100);
    replay(p);

    dm.getLeftTitle(p);
    verify(p);

    reset(p);
    // already processed - shouldn't process one more time
    replay(p);

    dm.getRightTitle(p);
    verify(p);
  }

  public void testDiffContents() {
    initModelOnSecondLineAndSelectRevisions(0, 1);

    DiffContent left = dm.getLeftDiffContent(new NullRevisionsProgress());
    DiffContent right = dm.getRightDiffContent(new NullRevisionsProgress());

    assertContent("b", left);
    assertContent("bc", right);
  }

  public void testDiffContentsAndTitleForCurrentRevision() {
    initModelOnSecondLineAndSelectRevisions(0, 0);

    assertEquals("Current", dm.getRightTitle(new NullRevisionsProgress()));

    DiffContent right = dm.getRightDiffContent(new NullRevisionsProgress());

    assertContent("bcd", right);
    assertTrue(right instanceof DocumentFragmentContent);
  }

  public void testDiffForDeletedAndRecreatedFile() throws Exception {
    byte[] bytes = f.contentsToByteArray();
    delete(f);

    f = createFile(f.getName(), new String(bytes));
    loadContent(f);

    initModelOnSecondLineAndSelectRevisions(3, 3);

    assertContent("b", dm.getLeftDiffContent(new NullRevisionsProgress()));
    assertContent("bcd", dm.getRightDiffContent(new NullRevisionsProgress()));
  }

  public void testRevert() throws IOException {
    initModelOnSecondLineAndSelectRevisions(0, 0);
    Reverter r = m.createReverter();
    r.revert();

    assertEquals("a\nbc\nc\ne\n", new String(f.contentsToByteArray()));
  }

  private void initModelOnSecondLineAndSelectRevisions(int first, int second) {
    m = new SelectionHistoryDialogModel(myProject, myGateway, getVcs(), f, 1, 1);
    m.selectRevisions(first, second);
    dm = m.getDifferenceModel();
  }
}
