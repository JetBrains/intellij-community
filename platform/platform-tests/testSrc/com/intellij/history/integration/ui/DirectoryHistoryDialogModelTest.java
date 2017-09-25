/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.history.integration.ui.models.DirectoryHistoryDialogModel;
import com.intellij.history.integration.ui.views.DirectoryChange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.List;

public class DirectoryHistoryDialogModelTest extends LocalHistoryUITestCase {
  private DirectoryHistoryDialogModel m;

  public void testTitle() {
    VirtualFile dir = createDirectory("dir");
    initModelFor(dir);
    assertEquals(FileUtil.toSystemDependentName(dir.getPath()), m.getTitle());
  }

  public void testNoDifference() {
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
