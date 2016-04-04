/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.history.integration.revertion;

import com.intellij.history.Label;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryException;
import com.intellij.history.integration.IntegrationTestCase;
import com.intellij.openapi.vfs.VirtualFile;

public class HistoryReverterToLabelTest extends IntegrationTestCase {

  public void testFileCreation() throws Exception {
    createChildData(myRoot, "first.txt");
    final Label testLabel = LocalHistory.getInstance().putSystemLabel(myProject, "testLabel");
    createChildData(myRoot, "foo.txt");
    revertToLabel(testLabel, myRoot);
    assertNull(myRoot.findChild("foo.txt"));
    assertNotNull(myRoot.findChild("first.txt"));
  }

  private void revertToLabel(Label testLabel, VirtualFile root) throws LocalHistoryException {
    testLabel.revert(myProject, root);
  }

  public void testFileCreationAsFirstAction() throws Exception {
    final Label testLabel = LocalHistory.getInstance().putSystemLabel(myProject, "testLabel");
    createChildData(myRoot, "foo.txt");
    revertToLabel(testLabel, myRoot);
    assertNull(myRoot.findChild("foo.txt"));
  }

  public void testPutLabelAndRevertInstantly() throws Exception {
    VirtualFile f = createChildData(myRoot, "foo.txt");
    setBinaryContent(f, new byte[]{123}, -1, 4000, this);
    final Label testLabel = LocalHistory.getInstance().putSystemLabel(myProject, "testLabel");
    revertToLabel(testLabel, myRoot);
    f = myRoot.findChild("foo.txt");
    assertNotNull(f);
    assertEquals(123, f.contentsToByteArray()[0]);
    assertEquals(4000, f.getTimeStamp());
  }

  public void testFileDeletion() throws Exception {
    VirtualFile f = createChildData(myRoot, "foo.txt");
    setBinaryContent(f, new byte[]{123}, -1, 4000, this);
    final Label testLabel = LocalHistory.getInstance().putSystemLabel(myProject, "testLabel");
    delete(f);
    revertToLabel(testLabel, myRoot);
    f = myRoot.findChild("foo.txt");
    assertNotNull(f);
    assertEquals(123, f.contentsToByteArray()[0]);
    assertEquals(4000, f.getTimeStamp());
  }

  public void testFileDeletionWithContent() throws Exception {
    VirtualFile f = createChildData(myRoot, "foo.txt");
    final Label testLabel = LocalHistory.getInstance().putSystemLabel(myProject, "testLabel");
    setBinaryContent(f, new byte[]{123}, -1, 4000, this);
    delete(f);
    revertToLabel(testLabel, myRoot);
    f = myRoot.findChild("foo.txt");
    assertNotNull(f);
    assertEquals(0, f.contentsToByteArray().length);
  }

  public void testParentAndChildRename() throws Exception {
    VirtualFile dir = createChildDirectory(myRoot, "dir");
    VirtualFile f = createChildData(dir, "foo.txt");
    int modificationStamp = -1;
    setBinaryContent(f, new byte[]{123}, modificationStamp, 4000, this);
    final LocalHistory localHistory = LocalHistory.getInstance();
    final Label testLabel1 = localHistory.putSystemLabel(myProject, "testLabel");
    rename(dir, "dir2");
    final Label testLabel2 = localHistory.putSystemLabel(myProject, "testLabel");
    rename(f, "bar.txt");

    revertToLabel(testLabel2, f);

    assertNotNull(myRoot.findChild("dir2"));
    dir = myRoot.findChild("dir2");

    assert dir != null;
    assertNull(dir.findChild("bar.txt"));
    f = dir.findChild("foo.txt");
    assertNotNull(f);
    assertEquals(123, f.contentsToByteArray()[0]);
    assertEquals(4000, f.getTimeStamp());

    revertToLabel(testLabel1, myRoot);
    assertNull(myRoot.findChild("dir2"));
    dir = myRoot.findChild("dir");

    assert dir != null;
    assertNull(dir.findChild("bar.txt"));
    f = dir.findChild("foo.txt");
    assertNotNull(f);
    assertEquals(123, f.contentsToByteArray()[0]);
    assertEquals(4000, f.getTimeStamp());
  }

  public void testRevertContentChange() throws Exception {
    VirtualFile f = createChildData(myRoot, "foo.txt");
    int modificationStamp1 = -1;
    setBinaryContent(f, new byte[]{1}, modificationStamp1, 1000, this);
    final Label testLabel = LocalHistory.getInstance().putSystemLabel(myProject, "testLabel");
    int modificationStamp = -1;
    setBinaryContent(f, new byte[]{2}, modificationStamp, 2000, this);
    setBinaryContent(f, new byte[]{3}, modificationStamp, 3000, this);
    revertToLabel(testLabel, myRoot);
    f = myRoot.findChild("foo.txt");
    assertNotNull(f);
    assertEquals(1, f.contentsToByteArray()[0]);
    assertEquals(1000, f.getTimeStamp());
  }

  public void testRevertContentChangeOnlyForFile() throws Exception {
    VirtualFile f = createChildData(myRoot, "foo.txt");
    int modificationStamp1 = -1;
    setBinaryContent(f, new byte[]{1}, modificationStamp1, 1000, this);
    VirtualFile f2 = createChildData(myRoot, "foo2.txt");
    setBinaryContent(f, new byte[]{1}, modificationStamp1, 1000, this);
    final Label testLabel = LocalHistory.getInstance().putSystemLabel(myProject, "testLabel");
    int modificationStamp = -1;
    setBinaryContent(f, new byte[]{2}, modificationStamp, 2000, this);
    setBinaryContent(f2, new byte[]{3}, modificationStamp, 3000, this);
    revertToLabel(testLabel, f);
    f = myRoot.findChild("foo.txt");
    assertNotNull(f);
    assertEquals(1, f.contentsToByteArray()[0]);
    assertEquals(1000, f.getTimeStamp());
    f2 = myRoot.findChild("foo2.txt");
    assertNotNull(f2);
    assertEquals(3, f2.contentsToByteArray()[0]);
    assertEquals(3000, f2.getTimeStamp());
  }
}
