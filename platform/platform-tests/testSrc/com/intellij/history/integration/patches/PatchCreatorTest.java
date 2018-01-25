// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.history.integration.patches;

import com.intellij.history.core.revisions.Difference;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.integration.PatchingTestCase;
import com.intellij.idea.Bombed;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.VfsTestUtil;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class PatchCreatorTest extends PatchingTestCase {
  @Bombed(user = "Nadya Zabrodina", year = 2018, month = Calendar.DECEMBER, day = 1,
    description = "Now we are not able to apply empty file creation patch; git special tag needed or smth like that")
  public void testCreationEmptyPatch() throws Exception {
    createChildData(myRoot, "f.txt");

    createPatchBetweenRevisions(1, 0);
    clearRoot();

    applyPatch();
    assertNotNull(myRoot.findChild("f.txt"));
  }

  public void testPatchBetweenTwoOldRevisions() throws Exception {
    createChildDataWithContent(myRoot, "f1.txt");
    createChildDataWithContent(myRoot, "f2.txt");
    createChildDataWithContent(myRoot, "f3.txt");

    createPatchBetweenRevisions(6, 1);
    clearRoot();
    applyPatch();
    myRoot.refresh(false, true);
    VirtualFile dir = myRoot.findChild("idea_test_idea_test_integration");
    assertThat(dir).isNotNull();
    assertThat(dir.findChild("f1.txt")).isNotNull();
    assertThat(dir.findChild("f2.txt")).isNotNull();
    assertThat(dir.findChild("f3.txt")).isNull();
  }

  public void testRename() throws Exception {
    VirtualFile f = createChildData(myRoot, "f.txt");
    setBinaryContent(f, new byte[]{'x'});

    rename(f, "ff.txt");

    createPatchBetweenRevisions(1, 0);
    rename(f, "f.txt");
    applyPatch();

    VirtualFile dir = myRoot.findChild("idea_test_idea_test_integration");
    VirtualFile patched = dir.findChild("ff.txt");
    assertNull(dir.findChild("f.txt"));
    assertNotNull(patched);
    assertEquals('x', patched.contentsToByteArray()[0]);
  }

  public void testReversePatch() throws Exception {
    createChildDataWithContent(myRoot, "f.txt");
    createPatchBetweenRevisions(2, 0, true);
    applyPatch();

    assertNull(myRoot.findFileByRelativePath("idea_test_idea_test_integration/f.txt"));
  }

  public void testDirectoryCreationWithFiles() throws Exception {
    VirtualFile dir = createChildDirectory(myRoot, "dir");
    createChildDataWithContent(dir, "f.txt");

    createPatchBetweenRevisions(2, 0, false);
    clearRoot();

    applyPatch();

    assertThat(myRoot.findFileByRelativePath("idea_test_idea_test_integration/dir/f.txt")).isNotNull();
  }

  public void testDirectoryDeletionWithFiles() throws Exception {
    VirtualFile dir = createChildDirectory(myRoot, "dir");
    createChildDataWithContent(dir, "f1.txt");
    createChildDataWithContent(dir, "f2.txt");

    VfsTestUtil.deleteFile(dir);
    createPatchBetweenRevisions(1, 0, false);

    dir = createChildDirectory(myRoot, "dir");
    createChildDataWithContent(dir, "f1.txt");
    createChildDataWithContent(dir, "f2.txt");

    applyPatch();

    assertNotNull(myRoot.findChild("dir"));
    assertNull(myRoot.findChild("dir").findChild("f1.txt"));
    assertNull(myRoot.findChild("dir").findChild("f2.txt"));
  }

  public void testDirectoryRename() throws Exception {
    VirtualFile dir = createChildDirectory(myRoot, "dir1");
    createChildDataWithContent(dir, "f.txt");

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
    List<Change> cc = new ArrayList<>();
    for (Difference d : dd) {
      Change c = new Change(d.getLeftContentRevision(myGateway), d.getRightContentRevision(myGateway));
      cc.add(c);
    }

    PatchCreator.create(myProject, cc, patchFilePath, reverse, null);
  }
}
