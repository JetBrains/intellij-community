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

package com.intellij.history.core.revisions;

import com.intellij.history.core.InMemoryLocalHistoryFacade;
import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.core.LocalHistoryTestCase;
import com.intellij.history.core.changes.ChangeSet;
import com.intellij.history.core.changes.CreateFileChange;
import com.intellij.history.core.tree.RootEntry;
import org.junit.Test;

public class RevisionsTest extends LocalHistoryTestCase {
  ChangeSet cs = cs("Action", new CreateFileChange(nextId(), "f"));

  @Test
  public void testCurrentRevisionIsBefore() {
    Revision r = new CurrentRevision(null, null);
    assertNull(r.getChangeSetName());
    assertNull(r.getChangeSetId());
  }

  @Test
  public void testRevisionAfterChangeIsBefore() {
    Revision r = new ChangeRevision(null, null, null, cs, false);
    assertEquals("Action", r.getChangeSetName());
    assertEquals(cs.getId(), r.getChangeSetId().intValue());
  }

  @Test
  public void testAfterRevisionForRootEntry() {
    RootEntry root = new RootEntry();
    LocalHistoryFacade facade = new InMemoryLocalHistoryFacade();

    ChangeSet cs = addChangeSet(facade, createFile(root, "f1"));
    addChangeSet(facade, createFile(root, "f2"));

    Revision r = new ChangeRevision(facade, root, "", cs, false);
    RootEntry e = (RootEntry)r.findEntry();

    assertEquals(e.getClass(), RootEntry.class);
    assertNotNull(e.findEntry("f1"));
    assertNull(e.findEntry("f2"));
  }
}
