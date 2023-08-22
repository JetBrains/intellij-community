// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.history.integration.patches;

import com.intellij.history.core.revisions.Difference;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.integration.PatchingTestCase;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.IdeaTextPatchBuilder;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.patch.PatchWriter;
import com.intellij.openapi.vfs.VirtualFile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class PatchCreatorTest extends PatchingTestCase {
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

    createPatchBetweenRevisions(6, 2);
    clearRoot();
    applyPatch();
    myRoot.refresh(false, true);
    assertNotNull(myRoot.findChild("f1.txt"));
    assertNotNull(myRoot.findChild("f2.txt"));
    assertNull(myRoot.findChild("f3.txt"));
  }

  public void testRename() throws Exception {
    VirtualFile f = createChildData(myRoot, "f.txt");
    setBinaryContent(f, new byte[]{'x'});

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
    createChildDataWithContent(myRoot, "f.txt");
    createPatchBetweenRevisions(2, 0, true);
    applyPatch();

    assertNull(myRoot.findChild("f.txt"));
  }

  public void testDirectoryCreationWithFiles() throws Exception {
    VirtualFile dir = createChildDirectory(myRoot, "dir");
    createChildDataWithContent(dir, "f.txt");

    createPatchBetweenRevisions(2, 0, false);
    clearRoot();

    applyPatch();

    assertThat(myRoot.findChild("dir")).isNotNull();
    assertThat(myRoot.findChild("dir").findChild("f.txt")).isNotNull();
  }

  public void testDirectoryDeletionWithFiles() throws Exception {
    VirtualFile dir = createChildDirectory(myRoot, "dir");
    createChildDataWithContent(dir, "f1.txt");
    createChildDataWithContent(dir, "f2.txt");

    delete(dir);
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

    List<Difference> differences = l.getDifferencesWith(r);
    List<Change> changes = new ArrayList<>();
    for (Difference d : differences) {
      Change c = new Change(d.getLeftContentRevision(myGateway), d.getRightContentRevision(myGateway));
      changes.add(c);
    }

    Path basePath = myRoot.toNioPath();
    List<FilePatch> patches = IdeaTextPatchBuilder.buildPatch(myProject, changes, basePath, reverse, false);
    PatchWriter.writePatches(myProject, patchFilePath, basePath, patches, null);
  }
}
