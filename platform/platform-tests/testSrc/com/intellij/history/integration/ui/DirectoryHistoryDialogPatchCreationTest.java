// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.history.integration.ui;

import com.intellij.history.integration.PatchingTestCase;
import com.intellij.history.integration.ui.models.DirectoryHistoryDialogModel;

import java.nio.charset.Charset;

public class DirectoryHistoryDialogPatchCreationTest extends PatchingTestCase {
  public void testPatchCreation() throws Exception {
    DirectoryHistoryDialogModel m = new DirectoryHistoryDialogModel(myProject, myGateway, getVcs(), myRoot);
    m.clearRevisions();
    createChildDataWithContent(myRoot, "f1.txt");
    createChildDataWithContent(myRoot, "f2.txt");
    createChildDataWithContent(myRoot, "f3.txt");
    assertSize(7, m.getRevisions());

    m.selectRevisions(0, 5);
    m.createPatch(patchFilePath, myProject.getBasePath(), false, Charset.defaultCharset());
    clearRoot();

    applyPatch();

    assertNotNull(myRoot.findChild("f1.txt"));
    assertNotNull(myRoot.findChild("f2.txt"));
    assertNull(myRoot.findChild("f3.txt"));
  }
}