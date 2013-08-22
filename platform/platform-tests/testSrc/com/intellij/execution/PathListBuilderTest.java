package com.intellij.execution;

import com.intellij.util.Assertion;
import com.intellij.util.PathsList;
import junit.framework.TestCase;

import java.io.File;

public class PathListBuilderTest extends TestCase {
  private final PathsList myBuilder = new PathsList();
  private final Assertion CHECK = new Assertion();

  public void testOrder() {
    myBuilder.add("a");
    myBuilder.addFirst("2");
    myBuilder.addTail("A");
    myBuilder.addFirst("1");
    myBuilder.add("b");
    myBuilder.addTail("B");
    CHECK.compareAll(new String[]{"1", "2", "a", "b", "A", "B"}, myBuilder.getPathList());
  }

  public void testDuplications() {
    myBuilder.add("b");
    myBuilder.add("b");

    myBuilder.addFirst("a");
    myBuilder.addFirst("a");

    myBuilder.addTail("c");
    myBuilder.addTail("c");

    CHECK.compareAll(new String[]{"a", "b", "c"}, myBuilder.getPathList());
  }

  public void testComplexDuplications() {
    myBuilder.add("a" + File.pathSeparatorChar + "b");
    myBuilder.add("c" + File.pathSeparatorChar + "b");
    CHECK.compareAll(new String[]{"a", "b", "c"}, myBuilder.getPathList());
  }

  public void testAddTwice() {
    myBuilder.add("a" + File.pathSeparatorChar + "a");
    myBuilder.add("b");
    CHECK.compareAll(new String[]{"a", "b"}, myBuilder.getPathList());
  }

  public void testAddFirstTwice() {
    myBuilder.addFirst("b" + File.pathSeparatorChar + "b");
    myBuilder.addFirst("a");
    CHECK.compareAll(new String[]{"a", "b"}, myBuilder.getPathList());
  }

  public void testAsString() {
    myBuilder.add("a" + File.pathSeparatorChar + "b" + File.pathSeparatorChar);
    myBuilder.add("c" + File.pathSeparatorChar);
    assertEquals("a" + File.pathSeparatorChar + "b" + File.pathSeparatorChar + "c", myBuilder.getPathsString());
  }
}
