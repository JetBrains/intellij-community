// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.TimeoutUtil;
import org.junit.Assert;
import org.junit.Test;

import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

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
    private static final Consumer<TreePath[]> SELECT_MAXIMALS = paths -> {
      measureExecutionTime("collectRoots", () -> TreeCollector.TreePathRoots.collect(paths));
      measureExecutionTime("collectLeafs", () -> TreeCollector.TreePathLeafs.collect(paths));
    };
    private static final Consumer<TreePath[]> FIND_COMMON_PATH = paths -> {
      measureExecutionTime("findCommonAncestor", () -> TreePathUtil.findCommonAncestor(paths));
      Assert.assertEquals(TreePathUtil.findCommonAncestor(paths), TreePathUtil.findCommonAncestor(paths));
    };

    // manual performance test
    public static void main(String[] args) {
      testPaths(FIND_COMMON_PATH, FAST, 10, 10);
      testPaths(FIND_COMMON_PATH, FAST, 1, 10);
      testPaths(FIND_COMMON_PATH, SLOW, 1, 1);
      testPaths(FIND_COMMON_PATH, SLOW, 1, 2);
      testPaths(FIND_COMMON_PATH, SLOW, 1, 3);
      testPaths(FIND_COMMON_PATH, SLOW, 1, 4);
      testPaths(FIND_COMMON_PATH, SLOW, 1, 5);
      testPaths(FIND_COMMON_PATH, SLOW, 1, 6);
      testPaths(FIND_COMMON_PATH, SLOW, 2, 6);
      testPaths(FIND_COMMON_PATH, SLOW, 3, 6);
      testPaths(FIND_COMMON_PATH, SLOW, 4, 6);
      testPaths(FIND_COMMON_PATH, SLOW, 5, 6);
      testPaths(FIND_COMMON_PATH, SLOW, 6, 6);

      testPaths(SELECT_MAXIMALS, FAST, 7, 7);
      testPaths(SELECT_MAXIMALS, FAST, 1, 7);
      testPaths(SELECT_MAXIMALS, SLOW, 1, 1);
      testPaths(SELECT_MAXIMALS, SLOW, 1, 2);
      testPaths(SELECT_MAXIMALS, SLOW, 1, 3);
      testPaths(SELECT_MAXIMALS, SLOW, 1, 4);
      testPaths(SELECT_MAXIMALS, SLOW, 2, 4);
      testPaths(SELECT_MAXIMALS, SLOW, 3, 4);
      testPaths(SELECT_MAXIMALS, SLOW, 4, 4);
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

    private static void testPaths(Consumer<TreePath[]> consumer, IntFunction<Object> function, int min, int max) {
      List<TreePath> list = new ArrayList<>();
      addPaths(function, min, max, list, null);
      System.err.println();
      System.err.println(list.size() + " paths with count from " + min + " to " + max);
      System.err.println("1. ordered list:");
      consumer.accept(TreePathUtil.toTreePathArray(list));
      Collections.reverse(list);
      System.err.println("2. reversed list:");
      consumer.accept(TreePathUtil.toTreePathArray(list));
      Collections.shuffle(list);
      System.err.println("3. shuffled list:");
      consumer.accept(TreePathUtil.toTreePathArray(list));
    }

    private static void measureExecutionTime(String method, Runnable runnable) {
      System.err.printf("%,12d ms to %s%n", TimeoutUtil.measureExecutionTime(runnable::run), method);
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
      TimeoutUtil.sleep(1); // simulate slow comparison
      return object instanceof Slow && ((Slow)object).id == id;
    }
  }
}
