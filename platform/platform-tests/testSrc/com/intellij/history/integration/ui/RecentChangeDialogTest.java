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

import com.intellij.history.core.revisions.RecentChange;
import com.intellij.history.integration.revertion.Reverter;
import com.intellij.history.integration.ui.models.RecentChangeDialogModel;
import com.intellij.history.integration.ui.views.RecentChangeDialog;
import com.intellij.openapi.util.Disposer;

import java.io.IOException;

public class RecentChangeDialogTest extends LocalHistoryUITestCase {
  public void testDialogWork() {
    getVcs().beginChangeSet();
    createChildData(myRoot, "f.txt");
    getVcs().endChangeSet("change");

    RecentChange c = getVcs().getRecentChanges(getRootEntry()).get(0);
    RecentChangeDialog d = null;

    try {
      d = new RecentChangeDialog(myProject, myGateway, c);
    }
    finally {
      if (d != null) {
        Disposer.dispose(d);
      }
    }
  }

  public void testRevertChange() throws IOException {
    getVcs().beginChangeSet();
    createChildData(myRoot, "f1.txt");
    getVcs().endChangeSet("change");

    getVcs().beginChangeSet();
    createChildData(myRoot, "f2.txt");
    getVcs().endChangeSet("another change");

    RecentChange c = getVcs().getRecentChanges(getRootEntry()).get(1);
    RecentChangeDialogModel m = new RecentChangeDialogModel(myProject, myGateway, getVcs(), c);

    Reverter r = m.createReverter();
    r.revert();

    assertNull(myRoot.findChild("f1.txt"));
    assertNotNull(myRoot.findChild("f2.txt"));
  }
}