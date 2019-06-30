// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree;

import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Assert;
import org.junit.Test;

import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import static com.intellij.util.TimeoutUtil.measureExecutionTime;
import static com.intellij.util.TimeoutUtil.sleep;
import static com.intellij.util.ui.tree.TreeUtil.selectMaximals;

public class TreeCollectorTest {
  private static final TreePath ROOT = new TreePath("root");
  private static final TreePath PARENT1 = ROOT.pathByAddingChild("parent1");
  private static final TreePath PARENT2 = ROOT.pathByAddingChild("parent2");
  private static final TreePath CHILD11 = PARENT1.pathByAddingChild("child1");
  private static final TreePath CHILD12 = PARENT1.pathByAddingChild("child2");
  private static final TreePath CHILD21 = PARENT2.pathByAddingChild("child1");
  private static final TreePath CHILD22 = PARENT2.pathByAddingChild("child2");


  @Test
  public void testTreePathLeafsCollect() {
    List<TreePath> list = new ArrayList<>();
    Collections.addAll(list, PARENT1, CHILD11, CHILD12, PARENT2, CHILD21, CHILD22);
    testCollect(list, TreeCollector.TreePathLeafs.collect(list), 4);
    Collections.reverse(list);
    testCollect(list, TreeCollector.TreePathLeafs.collect(list), 4);
    Collections.shuffle(list);
    testCollect(list, TreeCollector.TreePathLeafs.collect(list), 4);
  }

  @Test
  public void testTreePathRootsCollect() {
    List<TreePath> list = new ArrayList<>();
    Collections.addAll(list, PARENT1, CHILD11, CHILD12, PARENT2, CHILD21, CHILD22);
    testCollect(list, TreeCollector.TreePathRoots.collect(list), 2);
    Collections.reverse(list);
    testCollect(list, TreeCollector.TreePathRoots.collect(list), 2);
    Collections.shuffle(list);
    testCollect(list, TreeCollector.TreePathRoots.collect(list), 2);
  }

  private static <T> void testCollect(List<T> paths, List<T> collectedPaths, int count) {
    Assert.assertTrue(count <= paths.size());
    Assert.assertEquals(count, collectedPaths.size());
    int index = 0;
    for (T path : paths) {
      if (index >= count) {
        Assert.assertFalse("duplicated path found", collectedPaths.contains(path));
      }
      else if (path.equals(collectedPaths.get(index))) {
        index++;
      }
    }
  }


  @Test
  public void testTreePathLeafsCollectDuplicates() {
    testCollectDuplicates(TreeCollector.TreePathLeafs.collect(
      new TreePath("duplicated"),
      new TreePath("duplicated"),
      new TreePath("duplicated")));
  }

  @Test
  public void testTreePathRootsCollectDuplicates() {
    testCollectDuplicates(TreeCollector.TreePathRoots.collect(
      new TreePath("duplicated"),
      new TreePath("duplicated"),
      new TreePath("duplicated")));
  }

  private static void testCollectDuplicates(List<TreePath> list) {
    Assert.assertEquals(1, list.size());
  }


  @Test
  public void testTreePathLeafsCollectMutable() {
    testCollectMutable(TreeCollector.TreePathLeafs.collect());
    testCollectMutable(TreeCollector.TreePathLeafs.collect(new TreePath(new Object())));
  }

  @Test
  public void testTreePathRootsCollectMutable() {
    testCollectMutable(TreeCollector.TreePathRoots.collect());
    testCollectMutable(TreeCollector.TreePathRoots.collect(new TreePath(new Object())));
  }

  private static void testCollectMutable(List<TreePath> list) {
    list.add(new TreePath(new Object()));
    Assert.assertFalse(list.isEmpty());
    list.clear();
    Assert.assertTrue(list.isEmpty());
  }


  @Test
  public void testVirtualFileLeafsCollectNull() {
    testCollectNull(TreeCollector.VirtualFileLeafs.collect((VirtualFile)null));
    testCollectNull(TreeCollector.VirtualFileLeafs.collect((VirtualFile[])null));
  }

  @Test
  public void testVirtualFileRootsCollectNull() {
    testCollectNull(TreeCollector.VirtualFileRoots.collect((VirtualFile)null));
    testCollectNull(TreeCollector.VirtualFileRoots.collect((VirtualFile[])null));
  }

  @Test
  public void testTreePathLeafsCollectNull() {
    testCollectNull(TreeCollector.TreePathLeafs.collect((TreePath)null));
    testCollectNull(TreeCollector.TreePathLeafs.collect((TreePath[])null));
  }

  @Test
  public void testTreePathRootsCollectNull() {
    testCollectNull(TreeCollector.TreePathRoots.collect((TreePath)null));
    testCollectNull(TreeCollector.TreePathRoots.collect((TreePath[])null));
  }

  private static <T> void testCollectNull(List<T> list) {
    Assert.assertTrue(list.isEmpty());
  }


  @Test
  public void testVirtualFileLeafsCreate() {
    testCreate(TreeCollector.VirtualFileLeafs::create);
  }

  @Test
  public void testVirtualFileRootsCreate() {
    testCreate(TreeCollector.VirtualFileRoots::create);
  }

  @Test
  public void testTreePathLeafsCreate() {
    testCreate(TreeCollector.TreePathLeafs::create);
  }

  @Test
  public void testTreePathRootsCreate() {
    testCreate(TreeCollector.TreePathRoots::create);
  }

  private static <T> void testCreate(Supplier<TreeCollector<T>> supplier) {
    TreeCollector<T> one = create(supplier);
    TreeCollector<T> two = create(supplier);
    Assert.assertNotSame(one, two);
    Assert.assertNotEquals(one, two);
  }

  private static <T> TreeCollector<T> create(Supplier<TreeCollector<T>> supplier) {
    TreeCollector<T> collector = supplier.get();
    testCollectNull(collector.get());
    return collector;
  }


  public static final class Slow {
    private static final IntFunction<Object> SLOW = Slow::new;
    private static final IntFunction<Object> FAST = Integer::toString;

    // manual performance test
    public static void main(String[] args) {
      testPaths(FAST, 7, 7);
      testPaths(FAST, 1, 7);
      testPaths(SLOW, 1, 1);
      testPaths(SLOW, 1, 2);
      testPaths(SLOW, 1, 3);
      testPaths(SLOW, 1, 4);
      testPaths(SLOW, 2, 4);
      testPaths(SLOW, 3, 4);
      testPaths(SLOW, 4, 4);
    }

    private static void addPaths(IntFunction<Object> function, int min, int max, List<TreePath> list, TreePath path) {
      if (path == null) path = new TreePath(function.apply(0)); // create path to root node
      int count = path.getPathCount();
      if (min <= count) list.add(path);
      if (max <= count) return;
      for (int i = 0; i <= count; i++) {
        addPaths(function, min, max, list, path.pathByAddingChild(function.apply(i)));
      }
    }

    private static void testPaths(IntFunction<Object> function, int min, int max) {
      List<TreePath> list = new ArrayList<>();
      addPaths(function, min, max, list, null);
      System.err.println();
      System.err.println(list.size() + " paths with count from " + min + " to " + max);
      testPaths(list, "1. ordered");
      Collections.reverse(list);
      testPaths(list, "2. reversed");
      Collections.shuffle(list);
      testPaths(list, "3. shuffled");
    }

    private static void testPaths(List<TreePath> list, String description) {
      TreePath[] paths = TreePathUtil.toTreePathArray(list);
      System.err.println(description + " list:");
      System.err.println(String.format("%,12d ms to selectMaximals", measureExecutionTime(() -> selectMaximals(paths))));
      System.err.println(String.format("%,12d ms to collectRoots", measureExecutionTime(() -> TreeCollector.TreePathRoots.collect(paths))));
      System.err.println(String.format("%,12d ms to collectLeafs", measureExecutionTime(() -> TreeCollector.TreePathLeafs.collect(paths))));
      Assert.assertArrayEquals(selectMaximals(paths), TreePathUtil.toTreePathArray(TreeCollector.TreePathRoots.collect(paths)));
    }


    private final int id;

    private Slow(int id) {
      this.id = id;
    }

    @Override
    public String toString() {
      return String.valueOf(id);
    }

    @Override
    public int hashCode() {
      return id;
    }

    @Override
    public boolean equals(Object object) {
      sleep(1); // simulate slow comparison
      return object instanceof Slow && ((Slow)object).id == id;
    }
  }
}
