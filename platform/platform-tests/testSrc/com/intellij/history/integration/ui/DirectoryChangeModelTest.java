// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.history.integration.ui;

import com.intellij.history.core.revisions.Difference;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.tree.DirectoryEntry;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.IntegrationTestCase;
import com.intellij.history.integration.ui.models.DirectoryChangeModel;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.List;

public class DirectoryChangeModelTest extends IntegrationTestCase {
  public void testNames() throws IOException {
    VirtualFile f = createDirectory("foo");
    rename(f, "bar");

    List<Revision> revs = getRevisionsFor(f);

    Difference d = new Difference(false, revs.get(0).findEntry(), revs.get(1).findEntry());
    DirectoryChangeModel m = createModelOn(d);

    assertEquals("bar", m.getEntryName(0));
    assertEquals("foo", m.getEntryName(1));
  }

  public void testNamesForAbsentEntries() {
    Difference d = new Difference(false, null, null);
    DirectoryChangeModel m = createModelOn(d);

    assertEquals("", m.getEntryName(0));
    assertEquals("", m.getEntryName(1));
  }

  public void testCanShowFileDifference() throws IOException {
    VirtualFile f = createFile("foo.txt");
    setContent(f, "xxx");

    List<Revision> revs = getRevisionsFor(f);

    Difference d1 = new Difference(true, revs.get(0).findEntry(), revs.get(1).findEntry());
    Difference d2 = new Difference(true, null, revs.get(1).findEntry());
    Difference d3 = new Difference(true, revs.get(1).findEntry(), null);

    assertTrue(createModelOn(d1).canShowFileDifference());
    assertTrue(createModelOn(d2).canShowFileDifference());
    assertTrue(createModelOn(d3).canShowFileDifference());
  }

  public void testCanNotShowFileDifferenceForDirectories() {
    Entry left = new DirectoryEntry("left");
    Entry right = new DirectoryEntry("right");

    Difference d = new Difference(false, left, right);
    assertFalse(createModelOn(d).canShowFileDifference());
  }

  private DirectoryChangeModel createModelOn(Difference d) {
    return new DirectoryChangeModel(d, myGateway);
  }
}
