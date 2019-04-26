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

package com.intellij.history.core;

import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.tree.RootEntry;
import org.junit.Test;

import java.util.List;

public class LocalVcsLabelsTest extends LocalHistoryTestCase {
  LocalHistoryFacade myVcs = new InMemoryLocalHistoryFacade();
  RootEntry myRoot = new RootEntry();

  @Test
  public void testUserLabels() {
    add(myVcs, createFile(myRoot, "file"));
    myVcs.putUserLabel("1", "project");
    add(myVcs, changeContent(myRoot, "file", null));
    myVcs.putUserLabel("2", "project");

    List<Revision> rr = collectRevisions(myVcs, myRoot, "file", "project", null);
    assertEquals(5, rr.size());

    assertEquals("2", rr.get(1).getLabel());
    assertNull(rr.get(2).getLabel());
    assertEquals("1", rr.get(3).getLabel());
    assertNull(rr.get(4).getLabel());
  }

  @Test
  public void testLabelTimestamps() {
    setCurrentTimestamp(10);
    add(myVcs, createFile(myRoot, "file"));

    setCurrentTimestamp(20);
    myVcs.putUserLabel("", "project");

    setCurrentTimestamp(30);
    myVcs.putUserLabel("", "project");

    List<Revision> rr = collectRevisions(myVcs, myRoot, "file", "project", null);
    assertEquals(30, rr.get(1).getTimestamp());
    assertEquals(20, rr.get(2).getTimestamp());
    assertEquals(10, rr.get(3).getTimestamp());
  }

  @Test
  public void testContent() {
    add(myVcs, createFile(myRoot, "file", "one"));
    myVcs.putUserLabel("", "project");
    add(myVcs, changeContent(myRoot, "file", "two"));
    myVcs.putUserLabel("", "project");

    List<Revision> rr = collectRevisions(myVcs, myRoot, "file", "project", null);

    assertContent("two", rr.get(0).findEntry());
    assertContent("one", rr.get(2).findEntry());
  }

  @Test
  public void testGlobalUserLabels() {
    add(myVcs, createFile(myRoot, "one"));
    myVcs.putUserLabel("1", "project");
    add(myVcs, createFile(myRoot, "two"));
    myVcs.putUserLabel("2", "project");

    List<Revision> rr = collectRevisions(myVcs, myRoot, "one", "project", null);
    assertEquals(4, rr.size());
    assertEquals("2", rr.get(1).getLabel());
    assertEquals("1", rr.get(2).getLabel());

    rr = collectRevisions(myVcs, myRoot, "two", "project", null);
    assertEquals(3, rr.size());
    assertEquals("2", rr.get(1).getLabel());
  }

  @Test
  public void testGlobalLabelTimestamps() {
    setCurrentTimestamp(10);
    add(myVcs, createFile(myRoot, "file"));
    setCurrentTimestamp(20);
    myVcs.putUserLabel("", "project");

    List<Revision> rr = collectRevisions(myVcs, myRoot, "file", "project", null);
    assertEquals(20, rr.get(1).getTimestamp());
    assertEquals(10, rr.get(2).getTimestamp());
  }

  @Test
  public void testLabelsDuringChangeSet() {
    add(myVcs, createFile(myRoot, "file"));
    myVcs.beginChangeSet();
    add(myVcs, changeContent(myRoot, "file", null));
    myVcs.putUserLabel("label", "project");
    myVcs.endChangeSet("changeSet");

    List<Revision> rr = collectRevisions(myVcs, myRoot, "file", "project", null);
    assertEquals(3, rr.size());
    assertEquals("changeSet", rr.get(1).getChangeSetName());
    assertEquals(null, rr.get(2).getChangeSetName());
  }

  @Test
  public void testSystemLabels() {
    myVcs.created("f1", false);
    myVcs.created("f2", false);

    setCurrentTimestamp(123);
    myVcs.putSystemLabel("label", "project", 456);

    List<Revision> rr1 = collectRevisions(myVcs, myRoot, "f1", "project", null);
    List<Revision> rr2 = collectRevisions(myVcs, myRoot, "f2", "project", null);
    assertEquals(3, rr1.size());
    assertEquals(3, rr2.size());

    assertEquals("label", rr1.get(1).getLabel());
    assertEquals("label", rr2.get(1).getLabel());

    Revision r = rr1.get(1);
    assertEquals(123, r.getTimestamp());
    assertEquals(456, r.getLabelColor());
  }

  @Test
  public void testGettingByteContent() {
    LabelImpl l1 = myVcs.putSystemLabel("label", "project", -1);
    add(myVcs, createFile(myRoot, "f", "one"));

    LabelImpl l2 = myVcs.putSystemLabel("label", "project", -1);
    add(myVcs, changeContent(myRoot, "f", "two"));

    LabelImpl l3 = myVcs.putSystemLabel("label", "project", -1);

    assertNull(l1.getByteContent(myRoot, "f").getBytes());
    assertEquals("one", new String(l2.getByteContent(myRoot, "f").getBytes()));
    assertEquals("two", new String(l3.getByteContent(myRoot, "f").getBytes()));

    add(myVcs, createDirectory(myRoot, "dir"));
    LabelImpl l4 = myVcs.putSystemLabel("label", "project", -1);

    assertTrue(l4.getByteContent(myRoot, "dir").isDirectory());
    assertNull(l4.getByteContent(myRoot, "dir").getBytes());
  }
  
  @Test
  public void testGettingByteContentInsideChangeSet() {
    myVcs.beginChangeSet();
    add(myVcs, createFile(myRoot, "f", "one"));
    LabelImpl l1 = myVcs.putSystemLabel("label", "project", -1);
    add(myVcs, changeContent(myRoot, "f", "two"));
    LabelImpl l2 = myVcs.putSystemLabel("label", "project", -1);
    myVcs.endChangeSet(null);

    assertEquals("one", new String(l1.getByteContent(myRoot, "f").getBytes()));
    assertEquals("two", new String(l2.getByteContent(myRoot, "f").getBytes()));
  }

  @Test
  public void testGettingByteContentAfterRename() {
    add(myVcs, createFile(myRoot, "f", "one"));
    LabelImpl l1 = myVcs.putSystemLabel("label", "project", -1);

    add(myVcs, changeContent(myRoot, "f", "two"));
    LabelImpl l2 = myVcs.putSystemLabel("label", "project", -1);
    add(myVcs, rename(myRoot, "f", "f_r"));

    LabelImpl l3 = myVcs.putSystemLabel("label", "project", -1);

    assertEquals("one", new String(l1.getByteContent(myRoot, "f_r").getBytes()));
    assertEquals("two", new String(l2.getByteContent(myRoot, "f_r").getBytes()));
    assertEquals("two", new String(l3.getByteContent(myRoot, "f_r").getBytes()));
  }
}