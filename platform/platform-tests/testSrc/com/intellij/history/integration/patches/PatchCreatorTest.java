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

package com.intellij.history.integration.patches;

import com.intellij.history.core.revisions.Difference;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.integration.PatchingTestCase;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.List;

public class PatchCreatorTest extends PatchingTestCase {
  public void testCreationPatch() throws Exception {
    createChildData(myRoot, "f.txt");

    createPatchBetweenRevisions(1, 0);
    clearRoot();

    applyPatch();
    assertNotNull(myRoot.findChild("f.txt"));
  }

  public void testPatchBetweenTwoOldRevisions() throws Exception {
    createChildData(myRoot, "f1.txt");
    createChildData(myRoot, "f2.txt");
    createChildData(myRoot, "f3.txt");

    createPatchBetweenRevisions(3, 1);
    clearRoot();
    applyPatch();

    assertNotNull(myRoot.findChild("f1.txt"));
    assertNotNull(myRoot.findChild("f2.txt"));
    assertNull(myRoot.findChild("f3.txt"));
  }

  public void testRename() throws Exception {
    VirtualFile f = createChildData(myRoot, "f.txt");
    setBinaryContent(f,new byte[]{'x'});

    rename(f, "ff.txt");

    createPatchBetweenRevisions(1, 0);
    rename(f, "f.txt");
    applyPatch();

    VirtualFile patched = myRoot.findChild("ff.txt");
    assertNull(myRoot.findChild("f.txt"));
    assertNotNull(patched);
    assertEquals('x', patched.contentsToByteArray()[0]);
  }

  public void testReversePatch() throws Exception {
    createChildData(myRoot, "f.txt");

    createPatchBetweenRevisions(1, 0, true);
    applyPatch();

    assertNull(myRoot.findChild("f.txt"));
  }

  public void testDirectoryCreationWithFiles() throws Exception {
    VirtualFile dir = createChildDirectory(myRoot, "dir");
    createChildData(dir, "f.txt");

    createPatchBetweenRevisions(2, 0, false);
    clearRoot();

    applyPatch();

    assertNotNull(myRoot.findChild("dir"));
    assertNotNull(myRoot.findChild("dir").findChild("f.txt"));
  }

  public void testDirectoryDeletionWithFiles() throws Exception {
    VirtualFile dir = createChildDirectory(myRoot, "dir");
    createChildData(dir, "f1.txt");
    createChildData(dir, "f2.txt");

    delete(dir);
    createPatchBetweenRevisions(1, 0, false);

    dir = createChildDirectory(myRoot, "dir");
    createChildData(dir, "f1.txt");
    createChildData(dir, "f2.txt");

    applyPatch();

    assertNotNull(myRoot.findChild("dir"));
    assertNull(myRoot.findChild("dir").findChild("f1.txt"));
    assertNull(myRoot.findChild("dir").findChild("f2.txt"));
  }

  public void testDirectoryRename() throws Exception {
    VirtualFile dir = createChildDirectory(myRoot, "dir1");
    createChildData(dir, "f.txt");

    rename(dir, "dir2");

    createPatchBetweenRevisions(1, 0);

    rename(dir, "dir1");

    applyPatch();

    VirtualFile afterDir1 = myRoot.findChild("dir1");
    VirtualFile afterDir2 = myRoot.findChild("dir2");
    assertNotNull(afterDir1);
    assertNotNull(afterDir2);

    assertNull(afterDir1.findChild("f.txt"));
    assertNotNull(afterDir2.findChild("f.txt"));
  }

  private void createPatchBetweenRevisions(int left, int right) throws Exception {
    createPatchBetweenRevisions(left, right, false);
  }

  private void createPatchBetweenRevisions(int left, int right, boolean reverse) throws Exception {
    List<Revision> rr = getRevisionsFor(myRoot);
    Revision l = rr.get(left);
    Revision r = rr.get(right);

    List<Difference> dd = l.getDifferencesWith(r);
    List<Change> cc = new ArrayList<Change>();
    for (Difference d : dd) {
      Change c = new Change(d.getLeftContentRevision(myGateway), d.getRightContentRevision(myGateway));
      cc.add(c);
    }

    PatchCreator.create(myProject, cc, patchFilePath, reverse, null);
  }
}
