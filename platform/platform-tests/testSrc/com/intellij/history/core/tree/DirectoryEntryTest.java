/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.history.core.tree;

import com.intellij.history.core.LocalHistoryTestCase;
import com.intellij.history.core.Paths;
import com.intellij.history.core.StoredContent;
import com.intellij.history.core.revisions.Difference;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class DirectoryEntryTest extends LocalHistoryTestCase {
  @Test
  public void testAddingChildren() {
    Entry dir = new DirectoryEntry(null);
    Entry file = new FileEntry(null, null, -1, false);

    dir.addChild(file);

    assertEquals(1, dir.getChildren().size());
    assertSame(file, dir.getChildren().get(0));

    assertSame(dir, file.getParent());
  }

  @Test
  @Ignore
  public void testAddingExistentChildThrowsException() {
    Entry dir = new DirectoryEntry("dir");
    dir.addChild(new FileEntry("child", null, -1, false));

    Paths.setCaseSensitive(true);

    try {
      dir.addChild(new FileEntry("CHILD", null, -1, false));
    }
    catch (RuntimeException e) {
      fail();
    }

    try {
      dir.addChild(new FileEntry("child", null, -1, false));
      fail();
    }
    catch (RuntimeException e) {
      assertEquals("entry 'child' already exists in 'dir'", e.getMessage());
    }

    Paths.setCaseSensitive(false);

    try {
      dir.addChild(new FileEntry("CHILD", null, -1, false));
      fail();
    }
    catch (RuntimeException e) {
    }
  }

  @Test
  public void testRemovingChildren() {
    Entry dir = new DirectoryEntry(null);
    Entry file = new FileEntry(null, null, -1, false);

    dir.addChild(file);
    assertFalse(dir.getChildren().isEmpty());

    dir.removeChild(file);
    assertTrue(dir.getChildren().isEmpty());
    assertNull(file.getParent());
  }

  @Test
  public void testFindChild() {
    Entry dir = new DirectoryEntry(null);
    Entry one = new FileEntry("one", null, -1, false);
    Entry two = new FileEntry("two", null, -1, false);

    dir.addChild(one);
    dir.addChild(two);

    assertSame(one, dir.findChild("one"));
    assertSame(two, dir.findChild("two"));

    assertNull(dir.findChild("aaa"));

    Paths.setCaseSensitive(true);
    assertNull(dir.findChild("ONE"));

    Paths.setCaseSensitive(false);
    assertSame(one, dir.findChild("ONE"));
  }

  @Test
  public void testChildById() {
    DirectoryEntry dir = new DirectoryEntry(null);
    Entry file1 = new FileEntry("file1", null, -1, false);
    Entry file2 = new FileEntry("file2", null, -1, false);
    Entry dir1 = new DirectoryEntry("dir1");

    dir.addChild(file1);
    dir.addChild(file2);
    dir.addChild(dir1);

    assertSame(file1, dir.findDirectChild("file1", false));
    assertSame(file2, dir.findDirectChild("file2", false));
    assertSame(dir1, dir.findDirectChild("dir1", true));

    assertNull(dir.findDirectChild("file1", true));
  }

  @Test
  public void testPath() {
    Entry dir = new DirectoryEntry("dir");
    Entry file = new FileEntry("file", null, -1, false);

    dir.addChild(file);

    assertEquals("dir/file", file.getPath());
  }

  @Test
  public void testPathWithoutParent() {
    assertEquals("dir", new DirectoryEntry("dir").getPath());
    assertEquals("file", new FileEntry("file", null, -1, false).getPath());
  }

  @Test
  public void testCopyingWithContent() {
    Entry dir = new DirectoryEntry("name");
    Entry copy = dir.copy();

    assertEquals("name", copy.getPath());
  }

  @Test
  public void testDoesNotCopyParent() {
    Entry parent = new DirectoryEntry(null);
    Entry dir = new DirectoryEntry(null);

    parent.addChild(dir);

    assertNull(dir.copy().getParent());
  }

  @Test
  public void testCopyingContentRecursively() {
    Entry dir = new DirectoryEntry(null);
    Entry child1 = new FileEntry("child1", null, -1, false);
    Entry child2 = new DirectoryEntry("child2");
    Entry child3 = new FileEntry("child3", null, -1, false);

    dir.addChild(child1);
    dir.addChild(child2);
    child2.addChild(child3);

    Entry copy = dir.copy();
    List<Entry> children = copy.getChildren();

    assertEquals(2, children.size());
    assertEquals(1, children.get(1).getChildren().size());

    Entry copyChild1 = children.get(0);
    Entry copyChild2 = children.get(1);
    Entry copyChild3 = copyChild2.getChildren().get(0);

    assertSame(copy, copyChild1.getParent());
    assertSame(copy, copyChild2.getParent());
    assertSame(copyChild2, copyChild3.getParent());
  }

  @Test
  public void testCopyingContentDoesNotChangeOriginalStructure() {
    Entry dir = new DirectoryEntry(null);
    Entry child1 = new FileEntry("child1", null, -1, false);
    Entry child2 = new DirectoryEntry("child2");
    Entry child3 = new FileEntry("child3", null, -1, false);

    dir.addChild(child1);
    dir.addChild(child2);
    child2.addChild(child3);

    dir.copy();

    assertSame(dir, child1.getParent());
    assertSame(dir, child2.getParent());
    assertSame(child2, child3.getParent());
  }

  @Test
  public void testRenaming() {
    Entry e = new DirectoryEntry("name");
    e.setName("new name");
    assertEquals("new name", e.getName());
  }

  @Test
  public void testRenamingChildToNonExistentNameDoesNotThrowException() {
    Entry dir = new DirectoryEntry("dir");
    Entry child = new FileEntry("child", null, -1, false);
    dir.addChild(child);

    child.setName("new name");

    assertEquals("new name", child.getName());
  }

  @Test
  @Ignore
  public void testRenamingChildToExistingNameThrowsException() {
    Entry dir = new DirectoryEntry("dir");
    Entry child1 = new FileEntry("child1", null, -1, false);
    Entry child2 = new FileEntry("child2", null, -1, false);
    dir.addChild(child1);
    dir.addChild(child2);

    try {
      child1.setName("child2");
      fail();
    }
    catch (RuntimeException e) {
    }
  }

  @Test
  @Ignore
  public void testHasUnavailableContent() {
    Entry dir = new DirectoryEntry("dir");

    assertHasNoUnavailableContent(dir);

    dir.addChild(new FileEntry("f", c("abc"), -1, false));
    assertHasNoUnavailableContent(dir);

    FileEntry f1 = new FileEntry("f1", new StoredContent(-1), -1, false);
    FileEntry f2 = new FileEntry("f2", new StoredContent(-1), -1, false);

    DirectoryEntry subDir = new DirectoryEntry("subDir");
    dir.addChild(subDir);
    dir.addChild(f1);
    subDir.addChild(f2);

    assertHasUnavailableContent(dir, f2, f1);
    assertHasUnavailableContent(subDir, f2);
  }

  private void assertHasNoUnavailableContent(Entry dir) {
    List<Entry> ee = new ArrayList<>();
    assertFalse(dir.hasUnavailableContent(ee));
    assertTrue(ee.isEmpty());
  }

  private void assertHasUnavailableContent(Entry dir, Entry... entries) {
    List<Entry> ee = new ArrayList<>();

    assertTrue(dir.hasUnavailableContent(ee));
    assertEquals(entries, ee);
  }

  @Test
  public void testNoDifference() {
    DirectoryEntry e1 = new DirectoryEntry("name");
    DirectoryEntry e2 = new DirectoryEntry("name");

    assertTrue(Entry.getDifferencesBetween(e1, e2).isEmpty());
  }

  @Test
  public void testDifferenceInName() {
    DirectoryEntry e1 = new DirectoryEntry("name");
    DirectoryEntry e2 = new DirectoryEntry("another name");

    List<Difference> dd = Entry.getDifferencesBetween(e1, e2);
    assertEquals(1, dd.size());
    assertDirDifference(dd.get(0), e1, e2);
  }

  @Test
  public void testDifferenceInNameIsAlwaysCaseSensitive() {
    DirectoryEntry e1 = new DirectoryEntry("name");
    DirectoryEntry e2 = new DirectoryEntry("NAME");

    Paths.setCaseSensitive(false);
    assertEquals(1, Entry.getDifferencesBetween(e1, e2).size());

    Paths.setCaseSensitive(true);
    assertEquals(1, Entry.getDifferencesBetween(e1, e2).size());
  }

  @Test
  public void testDifferenceWithCreatedChild() {
    Entry e1 = new DirectoryEntry("name");
    Entry e2 = new DirectoryEntry("name");

    Entry child = new FileEntry("name", c("content"), -1, false);
    e2.addChild(child);

    List<Difference> dd = Entry.getDifferencesBetween(e1, e2);
    assertEquals(1, dd.size());
    assertFileDifference(dd.get(0), null, child);
  }

  @Test
  public void testDifferenceWithCreatedChildWithSubChildren() {
    Entry dir1 = new DirectoryEntry("name");
    Entry dir2 = new DirectoryEntry("name");

    Entry subDir = new DirectoryEntry("subDir");
    Entry subSubFile = new FileEntry("subSubFile", null, -1, false);

    dir2.addChild(subDir);
    subDir.addChild(subSubFile);

    List<Difference> dd = Entry.getDifferencesBetween(dir1, dir2);
    assertEquals(2, dd.size());
    assertDirDifference(dd.get(0), null, subDir);
    assertFileDifference(dd.get(1), null, subSubFile);
  }

  @Test
  public void testDifferenceWithDeletedChild() {
    Entry dir1 = new DirectoryEntry("name");
    Entry dir2 = new DirectoryEntry("name");

    Entry subDir = new DirectoryEntry("subDir");
    Entry subSubFile = new FileEntry("subSubFile", null, -1, false);

    dir1.addChild(subDir);
    subDir.addChild(subSubFile);

    List<Difference> dd = Entry.getDifferencesBetween(dir1, dir2);
    assertEquals(2, dd.size());
    assertDirDifference(dd.get(0), subDir, null);
    assertFileDifference(dd.get(1), subSubFile, null);
  }

  @Test
  public void testDifferenceWithModifiedChild() {
    Entry e1 = new DirectoryEntry("name");
    Entry e2 = new DirectoryEntry("name");

    Entry child1 = new FileEntry("name1", c("content1"), -1, false);
    Entry child2 = new FileEntry("name1", c("content2"), -1, false);

    e1.addChild(child1);
    e2.addChild(child2);

    List<Difference> dd = Entry.getDifferencesBetween(e1, e2);
    assertEquals(1, dd.size());
    assertFileDifference(dd.get(0), child1, child2);
  }

  @Test
  public void testNoesNotIncludeNonModifiedChildDifferences() {
    Entry e1 = new DirectoryEntry("name");
    Entry e2 = new DirectoryEntry("name");

    e1.addChild(new FileEntry("name", c("content"), -1, false));
    e1.addChild(new FileEntry("another name", c("content"), -1, false));

    e2.addChild(new FileEntry("name", c("content"), -1, false));

    List<Difference> dd = Entry.getDifferencesBetween(e1, e2);
    assertEquals("another name", dd.get(0).getLeft().getName());
    assertEquals(null, dd.get(0).getRight());
  }

  @Test
  public void testDifferenceWithNotModifiedChildWithDifferentIdentity() {
    Entry e1 = new DirectoryEntry("name");
    Entry e2 = new DirectoryEntry("name");

    e1.addChild(new FileEntry("name", c("content"), -1, false));
    e2.addChild(new FileEntry("name", c("content"), -1, false));

    assertTrue(Entry.getDifferencesBetween(e1, e2).isEmpty());
  }

  @Test
  public void testDifferenceWithModifiedBothSubjectAndChild() {
    Entry e1 = new DirectoryEntry("name1");
    Entry e2 = new DirectoryEntry("name2");

    Entry child1 = new FileEntry("name", c("content1"), -1, false);
    Entry child2 = new FileEntry("name", c("content2"), -1, false);

    e1.addChild(child1);
    e2.addChild(child2);

    List<Difference> dd = Entry.getDifferencesBetween(e1, e2);
    assertEquals(2, dd.size());

    assertDirDifference(dd.get(0), e1, e2);
    assertFileDifference(dd.get(1), child1, child2);
  }

  @Test
  public void testIncludesDifferenceForChildrenWhenParentWasModified() {
    Entry dir1 = new DirectoryEntry("dir1");
    Entry dir2 = new DirectoryEntry("dir2");

    Entry subDir1 = new DirectoryEntry("subDir");
    Entry subDir2 = new DirectoryEntry("subDir");

    Entry child1 = new FileEntry("name", c("content"), -1, false);
    Entry child2 = new FileEntry("name", c("content"), -1, false);

    dir1.addChild(subDir1);
    dir2.addChild(subDir2);
    subDir1.addChild(child1);
    subDir2.addChild(child2);

    List<Difference> dd = Entry.getDifferencesBetween(dir1, dir2);
    assertEquals(3, dd.size());

    assertDirDifference(dd.get(0), dir1, dir2);
    assertDirDifference(dd.get(1), subDir1, subDir2);
    assertFileDifference(dd.get(2), child1, child2);
  }

  private void assertDirDifference(Difference d, Entry left, Entry right) {
    assertDifference(d, left, right, false);
  }

  private void assertFileDifference(Difference d, Entry left, Entry right) {
    assertDifference(d, left, right, true);
  }

  private void assertDifference(Difference d, Entry left, Entry right, boolean isFile) {
    assertEquals(isFile, d.isFile());
    assertSame(left, d.getLeft());
    assertSame(right, d.getRight());
  }
}
