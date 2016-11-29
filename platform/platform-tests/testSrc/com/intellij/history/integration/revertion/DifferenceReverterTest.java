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

package com.intellij.history.integration.revertion;

import com.intellij.history.core.revisions.Difference;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.integration.IntegrationTestCase;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DifferenceReverterTest extends IntegrationTestCase {
  public void testFileCreation() throws Exception {
    createChildData(myRoot, "foo.txt");

    revertLastChange();

    assertNull(myRoot.findChild("foo.txt"));
  }

  public void testFileDeletion() throws Exception {
    VirtualFile f = createChildData(myRoot, "foo.txt");
    setBinaryContent(f, new byte[]{123}, -1, 4000, this);
    delete(f);

    revertLastChange();

    f = myRoot.findChild("foo.txt");
    assertNotNull(f);
    assertEquals(123, f.contentsToByteArray()[0]);
    assertEquals(4000, f.getTimeStamp());
  }

  public void testDirDeletion() throws Exception {
    VirtualFile dir = createChildDirectory(myRoot, "dir");
    VirtualFile subdir = createChildDirectory(dir, "subdir");
    VirtualFile f = createChildData(subdir, "foo.txt");
    int modificationStamp = -1;
    setBinaryContent(f, new byte[]{123}, modificationStamp, 4000, this);

    delete(dir);

    revertLastChange();

    dir = myRoot.findChild("dir");
    subdir = dir.findChild("subdir");
    f = subdir.findChild("foo.txt");
    assertNotNull(f);
    assertEquals(123, f.contentsToByteArray()[0]);
    assertEquals(4000, f.getTimeStamp());
  }

  public void testDeletionOfFileAndCreationOfDirAtTheSameTime() throws Exception {
    VirtualFile f = createChildData(myRoot, "foo.txt");

    getVcs().beginChangeSet();
    delete(f);
    createChildDirectory(myRoot, "foo.txt");
    getVcs().endChangeSet(null);

    revertLastChange();

    f = myRoot.findChild("foo.txt");
    assertNotNull(f);
    assertFalse(f.isDirectory());
  }

  public void testDeletionOfDirAndCreationOfFileAtTheSameTime() throws Exception {
    VirtualFile f = createChildDirectory(myRoot, "foo.txt");

    getVcs().beginChangeSet();
    delete(f);
    createChildData(myRoot, "foo.txt");
    getVcs().endChangeSet(null);

    revertLastChange();

    f = myRoot.findChild("foo.txt");
    assertNotNull(f);
    assertTrue(f.isDirectory());
  }

  public void testRename() throws Exception {
    VirtualFile f = createChildData(myRoot, "foo.txt");
    int modificationStamp = -1;
    setBinaryContent(f, new byte[]{123}, modificationStamp, 4000, this);
    rename(f, "bar.txt");

    revertLastChange();

    assertNull(myRoot.findChild("bar.txt"));
    f = myRoot.findChild("foo.txt");
    assertNotNull(f);
    assertEquals(123, f.contentsToByteArray()[0]);
    assertEquals(4000, f.getTimeStamp());
  }

  public void testMovement() throws Exception {
    VirtualFile dir1 = createChildDirectory(myRoot, "dir1");
    VirtualFile dir2 = createChildDirectory(myRoot, "dir2");

    VirtualFile f = createChildData(dir1, "foo.txt");
    int modificationStamp = -1;
    setBinaryContent(f, new byte[]{123}, modificationStamp, 4000, this);

    move(f, dir2);

    revertLastChange();

    assertNull(dir2.findChild("foo.txt"));
    f = dir1.findChild("foo.txt");
    assertNotNull(f);
    assertEquals(123, f.contentsToByteArray()[0]);
    assertEquals(4000, f.getTimeStamp());
  }

  public void testParentRename() throws Exception {
    VirtualFile dir = createChildDirectory(myRoot, "dir");
    VirtualFile f = createChildData(dir, "foo.txt");
    int modificationStamp = -1;
    setBinaryContent(f, new byte[]{123}, modificationStamp, 4000, this);

    rename(dir, "dir2");

    revertLastChange();

    assertNull(myRoot.findChild("dir2"));
    dir = myRoot.findChild("dir");
    f = dir.findChild("foo.txt");
    assertNotNull(f);
    assertEquals(123, f.contentsToByteArray()[0]);
    assertEquals(4000, f.getTimeStamp());
  }

  public void testParentAndChildRename() throws Exception {
    VirtualFile dir = createChildDirectory(myRoot, "dir");
    VirtualFile f = createChildData(dir, "foo.txt");
    int modificationStamp = -1;
    setBinaryContent(f, new byte[]{123}, modificationStamp, 4000, this);

    getVcs().beginChangeSet();
    rename(dir, "dir2");
    rename(f, "bar.txt");
    getVcs().endChangeSet(null);

    revertLastChange();

    assertNull(myRoot.findChild("dir2"));
    dir = myRoot.findChild("dir");

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
    int modificationStamp = -1;
    setBinaryContent(f, new byte[]{2}, modificationStamp, 2000, this);

    revertLastChange();

    f = myRoot.findChild("foo.txt");
    assertNotNull(f);
    assertEquals(1, f.contentsToByteArray()[0]);
    assertEquals(1000, f.getTimeStamp());
  }

  public void testContentChangeWhenDirectoryExists() throws Exception {
    VirtualFile f = createChildData(myRoot, "foo.txt");
    int modificationStamp1 = -1;
    setBinaryContent(f, new byte[]{1}, modificationStamp1, 1000, this);

    getVcs().beginChangeSet();
    rename(f, "bar.txt");
    int modificationStamp = -1;
    setBinaryContent(f, new byte[]{2}, modificationStamp, 2000, this);
    getVcs().endChangeSet(null);

    createChildDirectory(myRoot, "foo.txt");

    revertChange(1, 0, 1);

    assertNull(myRoot.findChild("bar.txt"));
    f = myRoot.findChild("foo.txt");
    assertNotNull(f);
    assertFalse(f.isDirectory());
    assertEquals(1, f.contentsToByteArray()[0]);
    assertEquals(1000, f.getTimeStamp());
  }

  public void testRevertingFromOldRevisionsWhenFileAlreadyDeleted() throws Exception {
    VirtualFile f = createChildData(myRoot, "foo.txt");
    delete(f);

    revertChange(1);

    assertNull(myRoot.findChild("foo.txt"));
  }

  public void testRevertingFromOldRevisionsWhenFileAlreadyExists() throws Exception {
    VirtualFile f = createChildData(myRoot, "foo.txt");
    delete(f);
    f = createChildData(myRoot, "foo.txt");

    revertChange(1);

    assertEquals(f, myRoot.findChild("foo.txt"));
  }

  public void testRevertingRenameFromOldRevisionsWhenDirDoesNotExists() throws Exception {
    VirtualFile dir = createChildDirectory(myRoot, "dir");
    VirtualFile f = createChildData(dir, "foo.txt");

    rename(f, "bar.txt");

    delete(dir);

    revertChange(1);

    dir = myRoot.findChild("dir");
    assertNotNull(dir);
    assertNotNull(dir.findChild("foo.txt"));
    assertNull(dir.findChild("bar.txt"));
  }

  public void testRevertingMoveFromOldRevisionsWhenDirDoesNotExists() throws Exception {
    VirtualFile dir1 = createChildDirectory(myRoot, "dir1");
    VirtualFile dir2 = createChildDirectory(myRoot, "dir2");
    VirtualFile f = createChildData(dir1, "foo.txt");

    move(f, dir2);

    delete(dir1);
    delete(dir2);

    revertChange(2);

    dir1 = myRoot.findChild("dir1");
    assertNotNull(dir1);
    assertNull(myRoot.findChild("dir2"));
    assertNotNull(dir1.findChild("foo.txt"));
  }

  public void testRevertingContentChangeFromOldRevisionsWhenDirDoesNotExists() throws Exception {
    VirtualFile dir = createChildDirectory(myRoot, "dir");
    VirtualFile f = createChildData(dir, "foo.txt");

    int modificationStamp1 = -1;
    setBinaryContent(f, new byte[]{1}, modificationStamp1, 1000, this);
    int modificationStamp = -1;
    setBinaryContent(f, new byte[]{2}, modificationStamp, 2000, this);

    delete(dir);

    revertChange(1);

    dir = myRoot.findChild("dir");
    assertNotNull(dir);
    f = dir.findChild("foo.txt");
    assertNotNull(f);
    assertEquals(1, f.contentsToByteArray()[0]);
    assertEquals(1000, f.getTimeStamp());
  }

  private void revertLastChange(int... diffsIndices) throws IOException {
    revertChange(0, diffsIndices);
  }

  private void revertChange(int change, int... diffsIndices) throws IOException {
    List<Revision> revs = getRevisionsFor(myRoot);
    Revision leftRev = revs.get(change + 1);
    Revision rightRev = revs.get(change);
    List<Difference> diffs = leftRev.getDifferencesWith(rightRev);
    List<Difference> toRevert = new ArrayList<>();
    for (int i : diffsIndices) {
      toRevert.add(diffs.get(i));
    }
    if (diffsIndices.length == 0) toRevert = diffs;
    new DifferenceReverter(myProject, getVcs(), myGateway, toRevert, leftRev).revert();
  }
}
