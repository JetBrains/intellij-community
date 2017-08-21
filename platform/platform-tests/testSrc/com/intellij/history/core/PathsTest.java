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

import com.intellij.util.containers.ContainerUtil;
import org.junit.Test;

public class PathsTest extends LocalHistoryTestCase {
  @Test
  public void testParent() {
    assertEquals("dir1/dir2", Paths.getParentOf("dir1/dir2/file"));
    assertEquals("", Paths.getParentOf("file"));
    assertEquals("c:", Paths.getParentOf("c:/file"));
    assertEquals("/", Paths.getParentOf("/file"));
    assertEquals("/dir", Paths.getParentOf("/dir/file"));
  }

  @Test
  public void testName() {
    assertEquals("file", Paths.getNameOf("file"));
    assertEquals("file", Paths.getNameOf("dir/file"));
    assertEquals("/", Paths.getNameOf("/"));
  }

  @Test
  public void testAppending() {
    assertEquals("file1/file2", Paths.appended("file1", "file2"));
    assertEquals("c:/root/file", Paths.appended("c:/root", "file"));
    assertEquals("/foo", Paths.appended("/", "foo"));
    assertEquals("/foo/bar", Paths.appended("/foo", "bar"));

    assertEquals("bar", Paths.appended("", "bar"));
  }

  @Test
  public void testRenaming() {
    assertEquals("dir/file2", Paths.renamed("dir/file1", "file2"));
    assertEquals("file2", Paths.renamed("file1", "file2"));
    assertEquals("/bar", Paths.renamed("/foo", "bar"));
  }

  @Test
  public void testRelative() {
    assertEquals("file", Paths.relativeIfUnder("dir/file", "dir"));

    assertNull(Paths.relativeIfUnder("dir/file", "abc"));
    assertNull(Paths.relativeIfUnder("dir/file", "di"));

    assertNull("dir/file", Paths.relativeIfUnder("/dir/file", "/"));
    assertEquals("file", Paths.relativeIfUnder("/dir/file", "/dir"));

    Paths.setCaseSensitive(true);
    assertNull(Paths.relativeIfUnder("dir/file", "DiR"));

    Paths.setCaseSensitive(false);
    assertEquals("file", Paths.relativeIfUnder("dir/file", "DiR"));
  }

  @Test
  public void testIsParentOf() {
    assertTrue(Paths.isParent("foo", "foo"));
    assertTrue(Paths.isParent("foo", "foo/bar"));
    assertTrue(Paths.isParent("foo/bar", "foo/bar"));
    assertTrue(Paths.isParent("foo/bar", "foo/bar/baz"));
    assertTrue(Paths.isParent("/", "/foo"));
    assertTrue(Paths.isParent("/foo", "/foo/bar"));
    assertFalse(Paths.isParent("foo/bar", "foo/baz"));
    assertFalse(Paths.isParent("foo/bar", "foo/barr"));
    assertFalse(Paths.isParent("foo/bar", "foo/barr/baz"));

    assertTrue(Paths.isParent("", "foo"));
  }

  @Test
  public void testIsParentOrChildOf() {
    assertTrue(Paths.isParentOrChild("foo/bar", "foo/bar"));
    assertTrue(Paths.isParentOrChild("foo/bar", "foo/bar/baz"));
    assertTrue(Paths.isParentOrChild("foo/bar/baz", "foo/bar"));
    assertTrue(Paths.isParentOrChild("/", "/foo/bar"));
    assertTrue(Paths.isParentOrChild("/foo/bar", "/"));
    assertFalse(Paths.isParentOrChild("foo/baz", "foo/bar"));
  }

  @Test
  public void testSplitting() {
    assertEquals(array("/", "foo", "bar"), ContainerUtil.collect(Paths.split("/foo/bar").iterator()));
    assertEquals(array("/", "foo", "bar"), ContainerUtil.collect(Paths.split("/foo/bar/").iterator()));
    assertEquals(array("foo", "bar"), ContainerUtil.collect(Paths.split("foo/bar/").iterator()));
    assertEquals(array("/", "foo"), ContainerUtil.collect(Paths.split("/foo").iterator()));
    assertEquals(array("/"), ContainerUtil.collect(Paths.split("/").iterator()));
    assertEquals(array("c:", "foo", "bar"), ContainerUtil.collect(Paths.split("c:/foo/bar").iterator()));

    assertEquals(array("//"), ContainerUtil.collect(Paths.split("//").iterator()));
    assertEquals(array("//foo"), ContainerUtil.collect(Paths.split("//foo").iterator()));
    assertEquals(array("//foo"), ContainerUtil.collect(Paths.split("//foo/").iterator()));
    assertEquals(array("//foo", "bar"), ContainerUtil.collect(Paths.split("//foo/bar").iterator()));
  }

  @Test
  public void testEquals() {
    assertTrue(Paths.equals("one", "one"));
    assertFalse(Paths.equals("one", "two"));

    Paths.setCaseSensitive(true);
    assertFalse(Paths.equals("one", "ONE"));

    Paths.setCaseSensitive(false);
    assertTrue(Paths.equals("one", "ONE"));
  }
}
