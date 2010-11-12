package com.intellij.openapi.diff.impl.splitter;

import junit.framework.TestCase;

public class TransformationTest extends TestCase {
  public void testLinear() {
    Transformation transformation = new LinearTransformation(8, 5);
    assertEquals(-3, transformation.transform(1));
    assertEquals(2, transformation.transform(2));
  }

  public void testOneToOne() {
    Interval range = Interval.fromTo(10, 12);
    assertEquals(10, LinearTransformation.oneToOne(1, 1, range));
    assertEquals(11, LinearTransformation.oneToOne(2, 1, range));
    assertEquals(11, LinearTransformation.oneToOne(3, 1, range));
  }
}
