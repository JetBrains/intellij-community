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

import com.intellij.history.integration.ui.models.DirectoryHistoryDialogModel;
import com.intellij.history.integration.ui.models.HistoryDialogModel;
import com.intellij.history.integration.ui.views.DirectoryChange;
import com.intellij.history.integration.ui.views.DirectoryHistoryDialog;
import com.intellij.openapi.util.Disposer;

import java.util.Collections;

public class DirectoryHistoryDialogTest extends LocalHistoryUITestCase {
  public void testDialogWorks() {
    DirectoryHistoryDialog d = new DirectoryHistoryDialog(myProject, myGateway, myRoot);
    Disposer.dispose(d);
  }

  public void testRevertion() throws Exception {
    createChildData(myRoot, "f.txt");

    HistoryDialogModel m = createModelAndSelectRevision(0);
    m.createReverter().revert();

    assertNull(myRoot.findChild("f.txt"));
  }

  public void testSelectionRevertion() throws Exception {
    createChildData(myRoot, "f1.txt");
    createChildData(myRoot, "f2.txt");

    DirectoryHistoryDialogModel m = createModelAndSelectRevision(1);
    DirectoryChange c = (DirectoryChange)m.getChanges().get(0);
    m.createRevisionReverter(Collections.singletonList(c.getDifference())).revert();

    assertNull(myRoot.findChild("f1.txt"));
    assertNotNull(myRoot.findChild("f2.txt"));
  }

  private DirectoryHistoryDialogModel createModelAndSelectRevision(int rev) {
    return createModelAndSelectRevisions(rev, rev);
  }

  private DirectoryHistoryDialogModel createModelAndSelectRevisions(int first, int second) {
    DirectoryHistoryDialogModel m = new DirectoryHistoryDialogModel(myProject, myGateway, getVcs(), myRoot);
    m.selectRevisions(first, second);
    return m;
  }
}
