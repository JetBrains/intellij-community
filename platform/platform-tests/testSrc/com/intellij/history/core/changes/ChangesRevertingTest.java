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

import com.intellij.history.core.LocalHistoryTestCase;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.RootEntry;
import org.junit.Test;

public class ChangesRevertingTest extends LocalHistoryTestCase {
  private final RootEntry root = new RootEntry();

  @Test
  public void testCreatingFile() {
    StructuralChange c = createFile(root, "file");
    assertTrue(root.hasEntry("file"));

    c.revertOn(root);
    assertFalse(root.hasEntry("file"));
  }

  @Test
  public void testCreatingDirectory() {
    StructuralChange c = createDirectory(root, "dir");
    assertTrue(root.hasEntry("dir"));

    c.revertOn(root);
    assertFalse(root.hasEntry("dir"));
  }

  @Test
  public void testCreatingFileUnderDirectory() {
    StructuralChange c = createFile(root, "dir/file", null, -1, false);

    c.revertOn(root);
    assertFalse(root.hasEntry("dir/file"));
    assertTrue(root.hasEntry("dir"));
  }

  @Test
  public void testChangingFileContent() {
    createFile(root, "file", "old content", 11L, false);
    StructuralChange c = changeContent(root, "file", "new content", 22L);

    c.revertOn(root);

    Entry e = root.getEntry("file");
    assertContent("old content", e.getContent());
    assertEquals(11L, e.getTimestamp());
  }

  @Test
  public void testRenamingFile() {
    createFile(root, "file");
    StructuralChange c = rename(root, "file", "new file");

    assertTrue(root.hasEntry("new file"));

    c.revertOn(root);
    assertTrue(root.hasEntry("file"));
    assertFalse(root.hasEntry("new file"));
  }

  @Test
  public void testRenamingDirectoryWithContent() {
    createFile(root, "dir1/dir2/file");

    StructuralChange c = rename(root, "dir1/dir2", "new dir");

    assertFalse(root.hasEntry("dir1/dir2/file"));
    assertTrue(root.hasEntry("dir1/new dir/file"));

    c.revertOn(root);

    assertTrue(root.hasEntry("dir1/dir2"));
    assertTrue(root.hasEntry("dir1/dir2/file"));
    assertFalse(root.hasEntry("dir1/new dir"));
  }

  @Test
  public void testChangingFileROStatus() {
    createFile(root, "f", null, -1, true);

    StructuralChange c = changeROStatus(root, "f", false);
    assertFalse(root.getEntry("f").isReadOnly());

    c.revertOn(root);
    assertTrue(root.getEntry("f").isReadOnly());
  }

  @Test
  public void testMovingFileFromOneDirectoryToAnother() {
    createDirectory(root, "dir1");
    createDirectory(root, "dir2");
    createFile(root, "dir1/file");

    StructuralChange c = move(root, "dir1/file", "dir2");

    assertTrue(root.hasEntry("dir2/file"));
    assertFalse(root.hasEntry("dir1/file"));

    c.revertOn(root);

    assertFalse(root.hasEntry("dir2/file"));
    assertTrue(root.hasEntry("dir1/file"));
  }

  @Test
  public void testMovingDirectory() {
    createFile(root, "root1/dir/file");
    createDirectory(root, "root2");

    StructuralChange c = move(root, "root1/dir", "root2");

    assertTrue(root.hasEntry("root2/dir"));
    assertTrue(root.hasEntry("root2/dir/file"));
    assertFalse(root.hasEntry("root1/dir"));

    c.revertOn(root);

    assertTrue(root.hasEntry("root1/dir"));
    assertTrue(root.hasEntry("root1/dir/file"));
    assertFalse(root.hasEntry("root2/dir"));
  }

  @Test
  public void testDeletingFile() {
    createFile(root, "file", "content", 18L, true);

    StructuralChange c = delete(root, "file");

    assertFalse(root.hasEntry("file"));

    c.revertOn(root);
    assertTrue(root.hasEntry("file"));

    Entry e = root.getEntry("file");

    assertContent("content", e.getContent());
    assertEquals(18L, e.getTimestamp());
    assertTrue(e.isReadOnly());
  }

  @Test
  public void testDeletionRevertionCopiesRestoredEntry() {
    createFile(root, "file");

    DeleteChange c = delete(root, "file");

    assertFalse(root.hasEntry("file"));

    c.revertOn(root);
    assertTrue(root.hasEntry("file"));

    Entry restored = root.findEntry("file");
    assertNotSame(restored, c.getDeletedEntry());

    c.getDeletedEntry().setName("fff");
    assertEquals("file", restored.getName());
  }

  @Test
  public void testDeletingDirectoryWithContent() {
    createFile(root, "dir1/dir2/file", "content", -1, false);

    StructuralChange c = delete(root, "dir1");
    assertFalse(root.hasEntry("dir1"));

    c.revertOn(root);

    assertTrue(root.hasEntry("dir1"));
    assertTrue(root.hasEntry("dir1/dir2"));
    assertTrue(root.hasEntry("dir1/dir2/file"));

    assertContent("content", root.getEntry("dir1/dir2/file").getContent());
  }
}
