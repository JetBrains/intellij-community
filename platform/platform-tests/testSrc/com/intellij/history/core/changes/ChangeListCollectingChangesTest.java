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

package com.intellij.history.core.changes;

import org.junit.Test;

import java.util.List;

public class ChangeListCollectingChangesTest extends ChangeListTestCase {
  @Test
  public void tesChangesForFile() {
    addChangeSet(facade, "1", createFile(r, "file"));
    addChangeSet(facade, "2", changeContent(r, "file", null));

    List<ChangeSet> result = getChangesFor("file");

    assertEquals(2, result.size());

    assertEquals("2", result.get(0).getName());
    assertEquals("1", result.get(1).getName());
  }

  @Test
  public void testSeveralChangesForSameFileInOneChangeSet() {
    addChangeSet(facade, createFile(r, "file"), changeContent(r, "file", null));

    assertEquals(1, getChangesFor("file").size());
  }

  @Test
  public void testChangeSetsWithChangesForAnotherFile() {
    addChangeSet(facade, createFile(r, "file1"), createFile(r, "file2"));

    assertEquals(1, getChangesFor("file1").size());
  }

  @Test
  public void testDoesNotIncludeNonrelativeChangeSet() {
    addChangeSet(facade, "1", createFile(r, "file1"));
    addChangeSet(facade, "2", createFile(r, "file2"));
    addChangeSet(facade, "3", changeContent(r, "file1", null));

    List<ChangeSet> result = getChangesFor("file1");
    assertEquals(2, result.size());

    assertEquals("3", result.get(0).getName());
    assertEquals("1", result.get(1).getName());
  }

  @Test
  public void testChangeSetsForDirectories() {
    add(facade, createDirectory(r, "dir"));
    add(facade, createFile(r, "dir/file"));

    assertEquals(2, getChangesFor("dir").size());
  }

  @Test
  public void testChangeSetsForDirectoriesWithFilesMovedAround() {
    addChangeSet(facade, "1", createDirectory(r, "dir1"), createDirectory(r, "dir2"));
    addChangeSet(facade, "2", createFile(r, "dir1/file"));
    addChangeSet(facade, "3", move(r, "dir1/file", "dir2"));

    List<ChangeSet> cc1 = getChangesFor("dir1");
    List<ChangeSet> cc2 = getChangesFor("dir2");

    assertEquals(3, cc1.size());
    assertEquals("3", cc1.get(0).getName());
    assertEquals("2", cc1.get(1).getName());
    assertEquals("1", cc1.get(2).getName());

    assertEquals(2, cc2.size());
    assertEquals("3", cc2.get(0).getName());
    assertEquals("1", cc2.get(1).getName());
  }

  @Test
  public void testChangeSetsForMovedFiles() {
    addChangeSet(facade, createDirectory(r, "dir1"), createDirectory(r, "dir2"));

    add(facade, createFile(r, "dir1/file"));
    add(facade, move(r, "dir1/file", "dir2"));

    assertEquals(2, getChangesFor("dir2/file").size());
  }

  @Test
  public void testChangingParentChangesItsChildren() {
    add(facade, createDirectory(r, "d"));
    add(facade, createFile(r, "d/file"));

    assertEquals(1, getChangesFor("d/file").size());

    add(facade, rename(r, "d", "dd"));

    assertEquals(2, getChangesFor("dd/file").size());
  }

  @Test
  public void testChangingPreviousParentDoesNotChangeItsChildren() {
    add(facade, createDirectory(r, "d1"));
    add(facade, createDirectory(r, "d2"));
    add(facade, createFile(r, "d1/file"));

    add(facade, move(r, "d1/file", "d2"));
    assertEquals(2, getChangesFor("d2/file").size());

    add(facade, rename(r, "d1", "d11"));
    assertEquals(2, getChangesFor("d2/file").size());
  }

  @Test
  public void testDoesNotIncludePreviousParentChanges() {
    add(facade, createDirectory(r, "dir"));
    add(facade, rename(r, "dir", "dir2"));
    add(facade, createFile(r, "dir2/file"));

    assertEquals(1, getChangesFor("dir2/file").size());
  }

  @Test
  public void testDoesNotIncludePreviousChangesForNewParent() {
    add(facade, createDirectory(r, "dir1"));
    add(facade, createFile(r, "dir1/file"));
    add(facade, createDirectory(r, "dir2"));
    add(facade, rename(r, "dir2", "dir3"));
    add(facade, move(r, "dir1/file", "dir3"));

    assertEquals(2, getChangesFor("dir3/file").size());
  }

  @Test
  public void testDoesNotIncludePreviousLabels() {
    facade.putUserLabel("label", "project");
    add(facade, createFile(r, "file"));
    assertEquals(1, getChangesFor("file").size());
  }

  @Test
  public void testChangesForComplexMovingCase() {
    addChangeSet(facade, createDirectory(r, "d1"),
                 createFile(r, "d1/file"),
                 createDirectory(r, "d1/d11"),
                 createDirectory(r, "d1/d12"),
                 createDirectory(r, "d2"));
    add(facade, move(r, "d1/file", "d1/d11"));
    add(facade, move(r, "d1/d11/file", "d1/d12"));

    assertEquals(3, getChangesFor("d1").size());
    assertEquals(3, getChangesFor("d1/d12/file").size());
    assertEquals(3, getChangesFor("d1/d11").size());
    assertEquals(2, getChangesFor("d1/d12").size());
    assertEquals(1, getChangesFor("d2").size());

    add(facade, new MoveChange(nextId(), "d2/d12", "d1"));

    assertEquals(4, getChangesFor("d1").size());
    assertEquals(4, getChangesFor("d2/d12/file").size());
    assertEquals(2, getChangesFor("d2").size());
    assertEquals(3, getChangesFor("d2/d12").size());
  }

  @Test
  public void testChangesForFileMovedIntoCreatedDir() {
    add(facade, createDirectory(r, "dir1"));

    ChangeSet cs1 = addChangeSet(facade, createFile(r, "dir1/file"));
    ChangeSet cs2 = addChangeSet(facade, createDirectory(r, "dir2"));
    ChangeSet cs3 = addChangeSet(facade, new MoveChange(nextId(), "dir2/file", "dir1"));

    assertEquals(array(cs3, cs1), getChangesFor("dir2/file"));
    assertEquals(array(cs3, cs2), getChangesFor("dir2"));
  }

  @Test
  public void testChangesForRestoreFile() {
    ChangeSet cs1 = addChangeSet(facade, createFile(r, "file"));
    ChangeSet cs2 = addChangeSet(facade, changeContent(r, "file", ""));
    ChangeSet cs3 = addChangeSet(facade, delete(r, "file"));
    ChangeSet cs4 = addChangeSet(facade, createFile(r, "file"));
    ChangeSet cs5 = addChangeSet(facade, changeContent(r, "file", "aaa"));

    assertEquals(array(cs5, cs4, cs3, cs2, cs1), getChangesFor("file"));
  }

  @Test
  public void testChangesForFileRestoredSeveralTimes() {
    ChangeSet cs1 = addChangeSet(facade, createFile(r, "file"));
    ChangeSet cs2 = addChangeSet(facade, delete(r, "file"));
    ChangeSet cs3 = addChangeSet(facade, createFile(r, "file"));
    ChangeSet cs4 = addChangeSet(facade, delete(r, "file"));
    ChangeSet cs5 = addChangeSet(facade, createFile(r, "file"));

    assertEquals(array(cs5, cs4, cs3, cs2, cs1), getChangesFor("file"));
  }

  @Test
  public void testChangesForRestoredDirectory() {
    ChangeSet cs1 = addChangeSet(facade, createDirectory(r, "dir"));
    ChangeSet cs2 = addChangeSet(facade, delete(r, "dir"));
    ChangeSet cs3 = addChangeSet(facade, createDirectory(r, "dir"));

    assertEquals(array(cs3, cs2, cs1), getChangesFor("dir"));
  }

  @Test
  public void testChangesForRestoredDirectoryWithRestoredChildren() {
    ChangeSet cs1 = addChangeSet(facade, createDirectory(r, "dir"));
    ChangeSet cs2 = addChangeSet(facade, createFile(r, "dir/file"));
    ChangeSet cs3 = addChangeSet(facade, delete(r, "dir"));
    ChangeSet cs4 = addChangeSet(facade, createDirectory(r, "dir"));
    ChangeSet cs5 = addChangeSet(facade, createFile(r, "dir/file"));

    assertEquals(array(cs5, cs4, cs3, cs2, cs1), getChangesFor("dir"));
    assertEquals(array(cs5, cs3, cs2), getChangesFor("dir/file"));
  }

  @Test
  public void testChangesForFileIfThereWereSomeDeletedFilesBeforeItsCreation() {
    ChangeSet cs1 = addChangeSet(facade, createFile(r, "f1"));
    ChangeSet cs2 = addChangeSet(facade, delete(r, "f1"));
    ChangeSet cs3 = addChangeSet(facade, createFile(r, "f2"));

    assertEquals(array(cs3), getChangesFor("f2"));
  }

  @Test
  public void testDoesNotIncludeChangeSetIfFileWasRestoredAndDeletedInOneChangeSet() {
    ChangeSet cs1 = addChangeSet(facade, createFile(r, "f"));
    ChangeSet cs2 = addChangeSet(facade, delete(r, "f"));
    ChangeSet cs3 = addChangeSet(facade, createFile(r, "f"), delete(r, "f"));
    ChangeSet cs4 = addChangeSet(facade, createFile(r, "f"));

    assertEquals(array(cs4, cs3, cs2, cs1), getChangesFor("f"));
  }

  @Test
  public void testIncludingChangeSetsWithLabelsInside() {
    ChangeSet cs1 = addChangeSet(facade, createFile(r, "f"));
    ChangeSet cs2 = addChangeSet(facade, new PutLabelChange(nextId(), "label", "project"));

    assertEquals(array(cs2, cs1), getChangesFor("f"));
  }

  @Test
  public void testDoesNotSplitChangeSetsWithLabelsInside() {
    ChangeSet cs1 = addChangeSet(facade, createFile(r, "f"));
    ChangeSet cs2 =
      addChangeSet(facade, changeContent(r, "f", null, -1), new PutLabelChange(nextId(), "label", "project"), changeContent(r, "f", null, -1));

    assertEquals(array(cs2, cs1), getChangesFor("f"));
  }

  @Test
  public void testDoesNotIncludeChangesMadeBetweenDeletionAndRestore() {
    ChangeSet cs1 = addChangeSet(facade, createFile(r, "file"));
    ChangeSet cs2 = addChangeSet(facade, delete(r, "file"));
    ChangeSet cs3 = addChangeSet(facade, new PutLabelChange(nextId(), "", "project"));
    ChangeSet cs4 = addChangeSet(facade, createFile(r, "file"));

    assertEquals(array(cs4, cs2, cs1), getChangesFor("file"));
  }

  @Test
  public void testDoesNotIgnoreDeletionOfChildren() {
    ChangeSet cs1 = addChangeSet(facade, createDirectory(r, "dir"));
    ChangeSet cs2 = addChangeSet(facade, createFile(r, "dir/file"));
    ChangeSet cs3 = addChangeSet(facade, delete(r, "dir/file"));

    assertEquals(array(cs3, cs2, cs1), getChangesFor("dir"));
  }

  @Test
  public void testChangesForRestoredFileWhenParentWasDeletedAfterDeletionOfTheFile() {
    ChangeSet cs1 = addChangeSet(facade, createDirectory(r, "dir1"));
    ChangeSet cs2 = addChangeSet(facade, createDirectory(r, "dir1/dir2"));
    ChangeSet cs3 = addChangeSet(facade, createFile(r, "dir1/dir2/file"));

    ChangeSet cs4 = addChangeSet(facade, delete(r, "dir1/dir2/file"));
    ChangeSet cs5 = addChangeSet(facade, delete(r, "dir1/dir2"));
    ChangeSet cs6 = addChangeSet(facade, delete(r, "dir1"));

    ChangeSet cs7 = addChangeSet(facade, createDirectory(r, "dir1"),
                                 createDirectory(r, "dir1/dir2"),
                                 createFile(r, "dir1/dir2/file"));

    assertEquals(array(cs7, cs4, cs3), getChangesFor("dir1/dir2/file"));
    assertEquals(array(cs7, cs5, cs4, cs3, cs2), getChangesFor("dir1/dir2"));
    assertEquals(array(cs7, cs6, cs5, cs4, cs3, cs2, cs1), getChangesFor("dir1"));
  }

  @Test
  public void testDoesNotIncludeChangesIfFileAndItsParentWasDeletedAndRestoredInOneChangeSet() {
    ChangeSet cs1 = addChangeSet(facade, createDirectory(r, "dir"),
                                 createFile(r, "dir/file"));

    ChangeSet cs2 = addChangeSet(facade, delete(r, "dir/file"),
                                 delete(r, "dir"));

    ChangeSet cs3 = addChangeSet(facade, createDirectory(r, "dir"),
                                 createFile(r, "dir/file"),
                                 delete(r, "dir/file"),
                                 delete(r, "dir"));

    ChangeSet cs4 = addChangeSet(facade, createDirectory(r, "dir"),
                                 createFile(r, "dir/file"));

    assertEquals(array(cs4, cs3, cs2, cs1), getChangesFor("dir/file"));
    assertEquals(array(cs4, cs3, cs2, cs1), getChangesFor("dir"));
  }

  @Test
  public void testFilteredChanges() {
    ChangeSet cs1 = addChangeSet(facade, createDirectory(r, "dir"));
    ChangeSet cs2 = addChangeSet(facade, createFile(r, "dir/FooBar"));
    ChangeSet cs3 = addChangeSet(facade, createFile(r, "dir/BarBaz"));

    assertEquals(array(cs2), getChangesFor("dir", "f"));
    assertEquals(array(cs2), getChangesFor("dir", "foo"));
    assertEquals(array(cs2), getChangesFor("dir", "FB"));
    assertEquals(array(cs3), getChangesFor("dir", "bar"));
    assertEquals(array(), getChangesFor("dir", "Baz"));
    assertEquals(array(cs3, cs2), getChangesFor("dir", "*Bar*"));
  }

  @Test
  public void testFilteredChangesDoesnTIncludeChanges() {
    ChangeSet cs1 = addChangeSet(facade, createDirectory(r, "dir"));
    ChangeSet cs2 = addChangeSet(facade, createFile(r, "dir/FooBar"));
    ChangeSet cs3 = addChangeSet(facade, createFile(r, "dir/BarBaz"));

    assertEquals(array(cs2), getChangesFor("dir", "f"));
    assertEquals(array(cs2), getChangesFor("dir", "foo"));
    assertEquals(array(cs2), getChangesFor("dir", "FB"));
    assertEquals(array(cs3), getChangesFor("dir", "bar"));
    assertEquals(array(), getChangesFor("dir", "Baz"));
    assertEquals(array(cs3, cs2), getChangesFor("dir", "*Bar*"));
  }

  private List<ChangeSet> getChangesFor(String path) {
    return getChangesFor(path, null);
  }

  private List<ChangeSet> getChangesFor(String path, String pattern) {
    return collectChanges(facade, path, "project", pattern);
  }
}
