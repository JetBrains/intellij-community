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

import com.intellij.history.integration.PatchingTestCase;
import com.intellij.history.integration.ui.models.DirectoryHistoryDialogModel;

import java.nio.charset.Charset;

public class DirectoryHistoryDialogPatchCreationTest extends PatchingTestCase {
  public void testPatchCreation() throws Exception {
    createChildData(myRoot, "f1.txt");
    createChildData(myRoot, "f2.txt");
    createChildData(myRoot, "f3.txt");

    DirectoryHistoryDialogModel m = new DirectoryHistoryDialogModel(myProject, myGateway, getVcs(), myRoot);
    assertSize(3, m.getRevisions());

    m.selectRevisions(0, 2);
    m.createPatch(patchFilePath, myProject.getBasePath(), false, Charset.defaultCharset());
    clearRoot();

    applyPatch();

    assertNotNull(myRoot.findChild("f1.txt"));
    assertNotNull(myRoot.findChild("f2.txt"));
    assertNull(myRoot.findChild("f3.txt"));
  }
}