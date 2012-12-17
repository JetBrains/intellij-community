/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.testFramework.vcs.MockContentRevision;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.File;

import static junit.framework.Assert.assertEquals;

/**
 * Testing only tree-view, because the list view is obvious.
 *
 * @author Kirill Likhodedov
 */
public class ChangesComparatorTest {

  @Test
  public void testInOneDirectory() throws Exception {
    assertEquals(-1, compare("~/project/A.java", "~/project/B.java"));
    assertEquals(1, compare("~/project/Z.java", "~/project/B.java"));
  }

  @Test
  public void testInDifferentDirs() throws Exception {
    assertEquals(-1, compare("~/project/aaa/A.java", "~/project/bbb/B.java"));
    assertEquals(-1, compare("~/project/aaa/B.java", "~/project/zzz/A.java"));
    assertEquals(1, compare("~/project/zzz/A.java", "~/project/aaa/B.java"));
  }

  @Test
  public void testInSubDir() throws Exception {
    // all folders precede plain files
    assertEquals("Folders should precede plain files", -1, compare("~/project/dir/subdir/A.java", "~/project/dir/B.java"));
    assertEquals("Folders should precede plain files", -1, compare("~/project/dir/A.java", "~/project/B.java"));
    assertEquals("Folders should precede plain files", 1, compare("~/project/B.java", "~/project/dir/subdir/A.java"));
  }

  @Test
  public void testEqualPaths() throws Exception {
    assertEquals("Equal paths should compare to 0", 0, compare("~/project/A.java", "~/project/A.java"));
    assertEquals("Equal paths should compare to 0", 0, compare("~/project/aaa/A.java", "~/project/aaa/A.java"));
  }

  private static int compare(String path1, String path2) throws Exception {
    return compare(change(path1), change(path2));
  }

  private static int compare(Change c1, Change c2) {
    int result = ChangesComparator.getInstance(false).compare(c1, c2);
    if (result > 0) {
      return 1;
    }
    if (result < 0) {
      return -1;
    }
    return 0;
  }

  @NotNull
  private static Change change(@NotNull String path) throws Exception {
    ContentRevision before = new MockContentRevision(new FilePathImpl(new File(path), false), VcsRevisionNumber.NULL);
    ContentRevision after = new MockContentRevision(new FilePathImpl(new File(path), false), VcsRevisionNumber.NULL);
    return new Change(before, after);
  }

}
