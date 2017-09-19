/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ui.tree;

import org.junit.Assert;
import org.junit.Test;

import javax.swing.tree.TreePath;

public class TreePathUtilTest {
  @Test
  public void createTreePath() {
    Object root = new Object();
    TreePath parent = TreePathUtil.createTreePath(null, root);
    Assert.assertNotNull(parent);
    Assert.assertNull(parent.getParentPath());
    Assert.assertSame(root, parent.getLastPathComponent());

    Object node = new Object();
    TreePath path = TreePathUtil.createTreePath(parent, node);
    Assert.assertNotNull(path);
    Assert.assertNotNull(path.getParentPath());
    Assert.assertNotSame(path, parent);
    Assert.assertSame(parent, path.getParentPath());
    Assert.assertSame(node, path.getLastPathComponent());
  }

  @Test
  public void convertTreePathToStrings() {
    String[] strings = TreePathUtil.convertTreePathToStrings(new TreePath(new Object[]{2, 1, 0}));
    Assert.assertNotNull(strings);
    Assert.assertEquals(3, strings.length);
    Assert.assertEquals("2", strings[0]);
    Assert.assertEquals("1", strings[1]);
    Assert.assertEquals("0", strings[2]);
  }

  @Test
  public void convertTreePathToArrayWrongConverter() {
    Assert.assertNull(TreePathUtil.convertTreePathToArray(new TreePath(new Object[]{2, 1, 0}), component -> null));
  }

  @Test
  public void convertTreePathToArrayWrongPathCount() {
    Assert.assertNull(TreePathUtil.convertTreePathToArray(new TreePath() {
    }));
  }

  @Test
  public void convertTreePathToArrayWrongPathComponent() {
    Assert.assertNull(TreePathUtil.convertTreePathToArray(new TreePath(new Object[]{2, 1, 0}) {
      @Override
      public Object getLastPathComponent() {
        return null;
      }
    }));
  }

  @Test
  public void convertTreePathToArrayDeep() {
    convertTreePathToArrayDeep(1);
    convertTreePathToArrayDeep(10);
    convertTreePathToArrayDeep(100);
    convertTreePathToArrayDeep(1000);
    convertTreePathToArrayDeep(10000);
    convertTreePathToArrayDeep(100000);
    convertTreePathToArrayDeep(1000000);
  }

  private static void convertTreePathToArrayDeep(int count) {
    TreePath path = null;
    for (int i = 0; i < count; i++) path = TreePathUtil.createTreePath(path, i);
    Assert.assertEquals(count, path.getPathCount());
    Assert.assertEquals(count - 1, path.getLastPathComponent());
    Object[] array = TreePathUtil.convertTreePathToArray(path);
    Assert.assertEquals(count, array.length);
    try {
      Assert.assertArrayEquals(array, path.getPath());
    }
    catch (StackOverflowError error) {
      System.out.println("StackOverflow - getPath: " + count);
    }
  }

  @Test
  public void convertArrayToTreePath() {
    TreePath path = TreePathUtil.convertArrayToTreePath("2", "1", "0");
    Assert.assertNotNull(path);
    Assert.assertEquals("0", path.getLastPathComponent());
    Assert.assertNotNull(path.getParentPath());
    Assert.assertEquals("1", path.getParentPath().getLastPathComponent());
    Assert.assertNotNull(path.getParentPath().getParentPath());
    Assert.assertEquals("2", path.getParentPath().getParentPath().getLastPathComponent());
    Assert.assertNull(path.getParentPath().getParentPath().getParentPath());
  }

  @Test
  public void convertArrayToTreePathConverter() {
    TreePath path = TreePathUtil.convertArrayToTreePath(new Object[]{2, 1, 0}, Object::toString);
    Assert.assertNotNull(path);
    Assert.assertEquals("0", path.getLastPathComponent());
    Assert.assertNotNull(path.getParentPath());
    Assert.assertEquals("1", path.getParentPath().getLastPathComponent());
    Assert.assertNotNull(path.getParentPath().getParentPath());
    Assert.assertEquals("2", path.getParentPath().getParentPath().getLastPathComponent());
    Assert.assertNull(path.getParentPath().getParentPath().getParentPath());
  }

  @Test
  public void convertArrayToTreePathEmptyArray() {
    Assert.assertNull(TreePathUtil.convertArrayToTreePath());
  }

  @Test
  public void convertArrayToTreePathWrongComponent() {
    Assert.assertNull(TreePathUtil.convertArrayToTreePath("2", null, "0"));
  }

  @Test
  public void convertArrayToTreePathWrongConverter() {
    Assert.assertNull(TreePathUtil.convertArrayToTreePath(new String[]{"2", "1", "0"}, component -> null));
  }

  @Test
  public void convertArrayToTreePathDeep() {
    convertArrayToTreePathDeep(1);
    convertArrayToTreePathDeep(10);
    convertArrayToTreePathDeep(100);
    convertArrayToTreePathDeep(1000);
    convertArrayToTreePathDeep(10000);
    convertArrayToTreePathDeep(100000);
    convertArrayToTreePathDeep(1000000);
  }

  private static void convertArrayToTreePathDeep(int count) {
    Object[] array = new Object[count];
    for (int i = 0; i < count; i++) array[i] = i;
    TreePath path = TreePathUtil.convertArrayToTreePath(array);
    Assert.assertEquals(count, path.getPathCount());
    Assert.assertEquals(count - 1, path.getLastPathComponent());
    try {
      Assert.assertEquals(path, new TreePath(array));
    }
    catch (StackOverflowError error) {
      System.out.println("StackOverflow - new TreePath: " + count);
    }
  }
}
