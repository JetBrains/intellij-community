// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.history.integration;

import com.intellij.history.LocalHistory;
import com.intellij.history.core.revisions.Difference;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.tree.Entry;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.TemporaryDirectory;
import com.intellij.testFramework.VfsTestUtil;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class RevisionsAndDiffsTest extends IntegrationTestCase {
  public void testRevisions() throws Exception {
    VirtualFile f = createFile("file.txt", "old");
    loadContent(f);
    setContent(f, "new");

    List<Revision> rr = getRevisionsFor(f);
    assertThat(rr).hasSize(3);
    assertContent("new", rr.get(0).findEntry());
    assertContent("old", rr.get(1).findEntry());
  }

  public void testNamedAndUnnamedCauseActions() throws Exception {
    getVcs().beginChangeSet();
    VirtualFile f = createFile("file.txt");
    getVcs().endChangeSet("name");

    setContent(f, "foo");

    List<Revision> rr = getRevisionsFor(f);
    assertThat(rr).hasSize(3);

    assertNull(rr.get(1).getChangeSetName());
    assertEquals("name", rr.get(2).getChangeSetName());
  }

  public void testIncludingCurrentVersionIntoRevisionsAfterPurge() throws Exception {
    Clock.setTime(10);
    VirtualFile f = createFile("file.txt", "content");
    loadContent(f);
    getVcs().getChangeListInTests().purgeObsolete(0);

    List<Revision> rr = getRevisionsFor(f);
    assertEquals(1, rr.size());

    Revision r = rr.get(0);
    assertNull(r.getLabel());
    assertNull(r.getChangeSetName());
    assertEquals(f.getTimeStamp(), r.getTimestamp());

    Entry e = r.findEntry();
    assertEquals("file.txt", e.getName());
    assertContent("content", e);
  }

  public void testCurrentRevisionForDirectoryAfterPurge() throws IOException {
    Clock.setTime(10);
    VirtualFile f = createDirectory("dir");
    getVcs().getChangeListInTests().purgeObsolete(0);

    List<Revision> rr = getRevisionsFor(f);
    assertEquals(1, rr.size());

    Revision r = rr.get(0);
    assertNull(r.getLabel());
    assertNull(r.getChangeSetName());
    assertEquals(-1, r.getTimestamp()); // directory has no timestamp

    Entry e = r.findEntry();
    assertEquals("dir", e.getName());
  }

  public void testIncludingVersionBeforeFirstChangeAfterPurge() throws IOException {
    Clock.setTime(10);
    VirtualFile f = createFile("file.txt", "one");
    loadContent(f);
    Clock.setTime(20);
    setContent(f, "two");

    getVcs().getChangeListInTests().purgeObsolete(5);

    List<Revision> rr = getRevisionsFor(f);
    assertEquals(2, rr.size());

    assertContent("two", rr.get(0).findEntry());
    assertContent("one", rr.get(1).findEntry());
  }

  public void testDoesNotIncludeRevisionsForAnotherEntries() throws IOException {
    getVcs().beginChangeSet();
    createFile("file1.txt");
    getVcs().endChangeSet("1");

    getVcs().beginChangeSet();
    VirtualFile f2 = createFile("file2.txt");
    getVcs().endChangeSet("2");

    List<Revision> rr = getRevisionsFor(f2);
    assertEquals(2, rr.size());
    assertEquals("2", rr.get(1).getChangeSetName());
  }

  public void testRevisionsTimestamp() throws IOException {
    Clock.setTime(10);
    VirtualFile f = createFile("file1.txt");

    Clock.setTime(20);
    setContent(f, "a");

    Clock.setTime(30);
    setContent(f, "b");

    List<Revision> rr = getRevisionsFor(f);
    assertEquals(30L, rr.get(1).getTimestamp());
    assertEquals(20L, rr.get(2).getTimestamp());
    assertEquals(10L, rr.get(3).getTimestamp());
  }

  public void testTimestampForCurrentRevisionAfterPurgeFromCurrentTimestamp() throws IOException {
    VirtualFile f = createFile("file.txt");
    getVcs().getChangeListInTests().purgeObsolete(0);

    assertEquals(f.getTimeStamp(), getRevisionsFor(f).get(0).getTimestamp());
  }

  public void testTimestampForLastRevisionAfterPurge() throws IOException {
    Clock.setTime(10);
    VirtualFile f = createFile("file1.txt");

    Clock.setTime(20);
    setContent(f, "a");

    Clock.setTime(30);
    setContent(f, "b");

    getVcs().getChangeListInTests().purgeObsolete(15);

    List<Revision> rr = getRevisionsFor(f);
    assertEquals(30L, rr.get(1).getTimestamp());
    assertEquals(20L, rr.get(2).getTimestamp());
  }

  public void testRevisionsForFileCreatedWithSameNameAsDeletedOne() throws IOException {
    VirtualFile f = createFile("file.txt", "old");
    loadContent(f);
    VfsTestUtil.deleteFile(f);
    f = createFile("file.txt", "new");
    loadContent(f);

    List<Revision> rr = getRevisionsFor(f);
    assertThat(rr).hasSize(4);

    Entry e = rr.get(0).findEntry();
    assertThat(e.getName()).isEqualTo("file.txt");
    assertContent("new", e);

    assertThat(rr.get(1).findEntry()).isNull();

    e = rr.get(2).findEntry();
    assertThat(e.getName()).isEqualTo("file.txt");
    assertContent("old", e);

    assertThat(rr.get(3).findEntry()).isNull();
  }

  public void testRevisionForDirectoryWithTheSameNameAsDeletedOne() {
    VirtualFile dir = createDirectory("dir");
    delete(dir);
    dir = createDirectory("dir");

    assertThat(getRevisionsFor(dir)).hasSize(4);
  }

  public void testRevisionForRestoredDirectoryWithRestoreChildren() throws IOException {
    VirtualFile dir = createDirectory("dir");
    createFile("dir/f.txt");
    delete(dir);

    getVcs().beginChangeSet();
    dir = createDirectory("dir");
    VirtualFile f = createFile("dir/f.txt");
    getVcs().endChangeSet(null);

    List<Revision> rr = getRevisionsFor(dir);
    assertEquals(5, rr.size());
    assertEquals(1, rr.get(0).findEntry().getChildren().size());
    assertNull(rr.get(1).findEntry());
    assertEquals(1, rr.get(2).findEntry().getChildren().size());
    assertEquals(0, rr.get(3).findEntry().getChildren().size());

    assertThat(getRevisionsFor(f)).hasSize(4);
  }

  public void testRevisionsForFileThatWasCreatedAndDeletedInOneChangeSet() throws IOException {
    getVcs().beginChangeSet();
    VirtualFile f = createFile("f.txt");
    getVcs().endChangeSet("1");
    delete(f);

    getVcs().beginChangeSet();
    f = createFile("f.txt");
    delete(f);
    getVcs().endChangeSet("2");

    getVcs().beginChangeSet();
    f = createFile("f.txt");
    getVcs().endChangeSet("3");

    getVcs().beginChangeSet();
    delete(f);
    f = createFile("f.txt");
    getVcs().endChangeSet("4");

    List<Revision> rr = getRevisionsFor(f);
    assertEquals(6, rr.size());
    assertEquals("4", rr.get(1).getChangeSetName());
    assertEquals("3", rr.get(2).getChangeSetName());
    assertEquals("2", rr.get(3).getChangeSetName());
    assertEquals(null, rr.get(4).getChangeSetName());
    assertEquals("1", rr.get(5).getChangeSetName());
  }

  public void testRevisionsForFileCreatedInPlaceOfRenamedOne() throws IOException {
    VirtualFile f = createFile("file1.txt", "content1");
    loadContent(f);
    rename(f, "file2.txt");
    VirtualFile ff = createFile("file1.txt", "content2");
    loadContent(ff);

    List<Revision> rr = getRevisionsFor(ff);
    assertEquals(2, rr.size());

    Entry e = rr.get(0).findEntry();
    assertEquals("file1.txt", e.getName());
    assertContent("content2", e);

    rr = getRevisionsFor(f);
    assertEquals(3, rr.size());

    e = rr.get(0).findEntry();
    assertEquals("file2.txt", e.getName());
    assertContent("content1", e);

    e = rr.get(1).findEntry();
    assertEquals("file1.txt", e.getName());
    assertContent("content1", e);
  }

  public void testRevisionsIfSomeFilesWereDeletedDuringChangeSet() throws IOException {
    VirtualFile dir = createDirectory("dir");
    VirtualFile f = createFile("dir/f.txt");
    getVcs().beginChangeSet();
    delete(f);

    List<Revision> rr = getRevisionsFor(dir);
    assertEquals(4, rr.size());
  }

  public void testGettingEntryFromRevisionInRenamedDir() {
    VirtualFile dir = createDirectory("dir");
    VirtualFile f = TemporaryDirectory.createVirtualFile(dir, "file.txt", null);
    rename(dir, "newDir");
    setContent(f, "xxx");

    List<Revision> rr = getRevisionsFor(f);
    assertEquals(4, rr.size());

    assertEquals(myRoot.getPath() + "/newDir/file.txt", rr.get(0).findEntry().getPath());
    assertEquals(myRoot.getPath() + "/newDir/file.txt", rr.get(1).findEntry().getPath());
    assertEquals(myRoot.getPath() + "/dir/file.txt", rr.get(2).findEntry().getPath());
  }

  public void testGettingDifferenceBetweenRevisions() throws IOException {
    VirtualFile f = createFile("file.txt", "one");
    loadContent(f);
    setContent(f, "two");

    List<Revision> rr = getRevisionsFor(f);

    Revision recent = rr.get(0);
    Revision prev = rr.get(1);

    List<Difference> dd = Revision.getDifferencesBetween(prev, recent);
    assertEquals(1, dd.size());

    Difference d = dd.get(0);
    assertContent("one", d.getLeft());
    assertContent("two", d.getRight());
  }

  public void testDifferenceForDirectory() throws IOException {
    VirtualFile dir = createDirectory("dir");
    VirtualFile f = createFile("dir/file.txt");

    List<Revision> rr = getRevisionsFor(dir);
    assertEquals(3, rr.size());

    Revision recent = rr.get(0);
    Revision prev = rr.get(1);

    List<Difference> dd = Revision.getDifferencesBetween(prev, recent);
    assertEquals(1, dd.size());

    Difference d = dd.get(0);
    assertNull(d.getLeft());
    assertEquals("file.txt", d.getRight().getName());
  }

  public void testNoDifferenceForDirectoryWithEqualContents() throws IOException {
    VirtualFile dir = createDirectory("dir");
    VirtualFile f = createFile("dir/file.txt");
    delete(f);

    List<Revision> rr = getRevisionsFor(dir);

    assertTrue(Revision.getDifferencesBetween(rr.get(0), rr.get(2)).isEmpty());
  }

  public void testDoesNotIncludeNotModifiedDifferences() throws IOException {
    getVcs().beginChangeSet();
    VirtualFile dir = createDirectory("dir1");
    createFile("dir1/dir2/file.txt");
    createDirectory("dir1/dir3");
    getVcs().endChangeSet(null);

    createFile("dir1/dir3/file.txt");

    List<Revision> rr = getRevisionsFor(dir);
    Revision recent = rr.get(0);
    Revision prev = rr.get(1);

    List<Difference> dd = Revision.getDifferencesBetween(prev, recent);
    assertEquals(1, dd.size());

    Difference d = dd.get(0);
    assertNull(d.getLeft());
    assertEquals(myRoot.getPath() + "/dir1/dir3/file.txt", d.getRight().getPath());
  }

  public void testFilteredRevisionsDoNotContainLabels() throws IOException {
    createFile("foo.txt");
    LocalHistory.getInstance().putSystemLabel(myProject, "1", -1);
    createFile("bar.txt");
    LocalHistory.getInstance().putSystemLabel(myProject, "2", -1);

    assertEquals(6, getRevisionsFor(myRoot).size());
    assertEquals(3, getRevisionsFor(myRoot, "*.txt").size());
  }

  public void testFilteredRevisionsIfNothingFound() throws Exception {
    createFile("foo.txt");
    assertEquals(3, getRevisionsFor(myRoot).size());
    assertEquals(1, getRevisionsFor(myRoot, "xxx").size());
  }

  public void testDoNotIncludeLabelsBeforeFirstRevision() throws Exception {
    LocalHistory.getInstance().putSystemLabel(myProject, "1", -1);
    VirtualFile f = createFile("foo.txt");
    LocalHistory.getInstance().putSystemLabel(myProject, "2", -1);
    assertEquals(3, getRevisionsFor(f).size());
  }

  public void testDoNotIncludeLabelsWhenFileDidNotExist() throws Exception {
    VirtualFile f = createFile("foo.txt");
    LocalHistory.getInstance().putSystemLabel(myProject, "1", -1);
    delete(f);
    LocalHistory.getInstance().putSystemLabel(myProject, "2", -1);
    f = createFile("foo.txt");
    LocalHistory.getInstance().putSystemLabel(myProject, "3", -1);

    assertEquals(6, getRevisionsFor(f).size());
  }

  public void testDeleteAndRestoreInTheSameChangeSet() throws Exception {
    String fileName = "foo.txt";

    VirtualFile file = createFile(fileName);

    getVcs().beginChangeSet();
    delete(file);
    VirtualFile restoredFile = createFile(fileName);
    getVcs().endChangeSet("delete and create file");

    assertEquals(3, getRevisionsFor(restoredFile).size());
  }
}
