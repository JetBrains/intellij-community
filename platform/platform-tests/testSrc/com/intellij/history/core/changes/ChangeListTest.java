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

import com.intellij.history.core.tree.RootEntry;
import org.junit.Test;

import java.util.List;

public class ChangeListTest extends ChangeListTestCase {
  @Test
  public void testRevertionUpToInclusively() {
    addChangeSet(facade, "1", createFile(r, "file1"));
    addChangeSet(facade, "2", createFile(r, "file2"));

    RootEntry copy = r.copy();
    facade.revertUpTo(copy, "", facade.getChangeListInTests().getChangesInTests().get(0), null, true, true);
    assertTrue(copy.hasEntry("file1"));
    assertFalse(copy.hasEntry("file2"));

    copy = r.copy();
    facade.revertUpTo(copy, "", facade.getChangeListInTests().getChangesInTests().get(1), null, true, true);
    assertFalse(copy.hasEntry("file1"));
    assertFalse(copy.hasEntry("file2"));
  }

  @Test
  public void testRevertionUpToExclusively() {
    addChangeSet(facade, "1", createFile(r, "file1"));
    addChangeSet(facade, "2", createFile(r, "file2"));

    RootEntry copy = r.copy();
    facade.revertUpTo(copy, "", facade.getChangeListInTests().getChangesInTests().get(0), null, false, true);
    assertTrue(copy.hasEntry("file1"));
    assertTrue(copy.hasEntry("file2"));

    copy = r.copy();
    facade.revertUpTo(copy, "", facade.getChangeListInTests().getChangesInTests().get(1), null, false, true);
    assertTrue(copy.hasEntry("file1"));
    assertFalse(copy.hasEntry("file2"));
  }

  @Test
  public void testRevertionUpToWithTrackingPath() {
    add(facade, createFile(r, "file1"));
    add(facade, createFile(r, "file2"));
    add(facade, rename(r, "file2", "file3"));

    RootEntry copy = r.copy();
    assertEquals("file3", facade.revertUpTo(copy, "file3", facade.getChangeListInTests().getChangesInTests().get(0), null, false, true));
    assertTrue(copy.hasEntry("file1"));
    assertFalse(copy.hasEntry("file2"));
    assertTrue(copy.hasEntry("file3"));

    copy = r.copy();
    assertEquals("file2", facade.revertUpTo(copy, "file3", facade.getChangeListInTests().getChangesInTests().get(1), null, false, true));
    assertTrue(copy.hasEntry("file1"));
    assertTrue(copy.hasEntry("file2"));
    assertFalse(copy.hasEntry("file3"));

    copy = r.copy();
    assertEquals("file2", facade.revertUpTo(copy, "file3", facade.getChangeListInTests().getChangesInTests().get(1), null, true, true));
    assertTrue(copy.hasEntry("file1"));
    assertFalse(copy.hasEntry("file2"));
    assertFalse(copy.hasEntry("file3"));
  }

  @Test
  public void testRevertionUpToWithTrackingPathWithDeletionAndMovements() {
    add(facade, createDirectory(r, "root"));
    add(facade, createDirectory(r, "root/dir1"));
    add(facade, rename(r, "root/dir1", "dir2"));
    add(facade, delete(r, "root/dir2"));
    add(facade, createDirectory(r, "root/dir2"));
    add(facade, rename(r, "root", "root2"));

    RootEntry copy = r.copy();
    assertEquals("root/dir2", facade.revertUpTo(copy, "root2/dir2", facade.getChangeListInTests().getChangesInTests().get(3), null, false,
                                                true));
    assertTrue(copy.hasEntry("root/dir2"));
    assertFalse(copy.hasEntry("root/dir1"));
    assertFalse(copy.hasEntry("root1"));

    copy = r.copy();
    assertEquals("root/dir1", facade.revertUpTo(copy, "root2/dir2", facade.getChangeListInTests().getChangesInTests().get(3), null, true,
                                                true));
    assertTrue(copy.hasEntry("root/dir1"));
    assertFalse(copy.hasEntry("root/dir2"));
    assertFalse(copy.hasEntry("root1"));
  }

  @Test
  public void testChangeSet() {
    facade.beginChangeSet();
    StructuralChange c1 = add(facade, createFile(r, "f1"));
    StructuralChange c2 = add(facade, createFile(r, "f2"));
    facade.endChangeSet("changeSet");

    List<ChangeSet> cc = facade.getChangeListInTests().getChangesInTests();
    assertEquals(1, cc.size());
    assertEquals("changeSet", cc.get(0).getName());
    assertEquals(ChangeSet.class, cc.get(0).getClass());
    assertEquals(array(c1, c2), cc.get(0).getChanges());
  }

  @Test
  public void testForcesBegin() {
    facade.beginChangeSet();
    add(facade, createFile(r, "f1"));
    facade.beginChangeSet();
    add(facade, createFile(r, "f2"));
    facade.forceBeginChangeSet();
    add(facade, createFile(r, "f3"));
    facade.endChangeSet("a");
    add(facade, createFile(r, "f4"));
    facade.endChangeSet("b");
    add(facade, createFile(r, "f5"));
    facade.endChangeSet("c");

    List<ChangeSet> cc = facade.getChangeListInTests().getChangesInTests();
    assertEquals(2, cc.size());
    assertEquals("c", cc.get(0).getName());
    assertEquals(3, cc.get(0).getChanges().size());
    assertEquals(null, cc.get(1).getName());
    assertEquals(2, cc.get(1).getChanges().size());
  }

  @Test
  public void testChangeSetTimestamp() {
    setCurrentTimestamp(123);
    facade.beginChangeSet();
    add(facade, createFile(r, "f"));
    setCurrentTimestamp(456);
    facade.endChangeSet(null);

    assertEquals(123, facade.getChangeListInTests().getChangesInTests().get(0).getTimestamp());
  }

  @Test
  public void testSkippingEmptyChangeSets() {
    facade.beginChangeSet();
    facade.endChangeSet(null);
    assertTrue(facade.getChangeListInTests().getChangesInTests().isEmpty());
  }

  @Test
  public void testSkippingInnerChangeSets() {

    facade.beginChangeSet();
    Change c1 = add(facade, createFile(r, "f1"));
    facade.beginChangeSet();
    Change c2 = add(facade, createFile(r, "f2"));
    facade.endChangeSet("inner");
    facade.endChangeSet("outer");

    List<ChangeSet> cc = facade.getChangeListInTests().getChangesInTests();
    assertEquals(1, cc.size());
    assertEquals("outer", cc.get(0).getName());
    assertEquals(ChangeSet.class, cc.get(0).getClass());
    assertEquals(array(c1, c2), cc.get(0).getChanges());
  }
}
