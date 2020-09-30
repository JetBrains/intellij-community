// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.history.integration.ui;

import com.intellij.history.integration.ui.models.DirectoryHistoryDialogModel;
import com.intellij.history.integration.ui.views.DirectoryChange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.List;

public class DirectoryHistoryDialogModelTest extends LocalHistoryUITestCase {
  private DirectoryHistoryDialogModel m;

  public void testTitle() throws IOException {
    VirtualFile dir = createDirectory("dir");
    initModelFor(dir);
    assertEquals(FileUtil.toSystemDependentName(dir.getPath()), m.getTitle());
  }

  public void testNoDifference() throws IOException {
    VirtualFile dir = createDirectory("dir");

    getVcs().getChangeListInTests().purgeObsolete(0);
    initModelFor(dir);

    assertSize(0, m.getRevisions());
    assertTrue(m.getChanges().isEmpty());
  }

  public void testDifference() throws IOException {
    VirtualFile dir = createDirectory("dir");
    createFile("dir/file.txt");

    initModelFor(dir);

    assertEquals(2, m.getRevisions().size());

    m.selectRevisions(1, 1);
    List<Change> cc = m.getChanges();
    assertEquals(2, cc.size());
    assertEquals("dir", ((DirectoryChange)cc.get(0)).getModel().getEntryName(1));
    assertEquals("file.txt", ((DirectoryChange)cc.get(1)).getModel().getEntryName(1));
  }

  private void initModelFor(VirtualFile dir) {
    m = new DirectoryHistoryDialogModel(myProject, myGateway, getVcs(), dir);
  }
}
