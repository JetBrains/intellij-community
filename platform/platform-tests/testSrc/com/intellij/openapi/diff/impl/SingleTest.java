package com.intellij.openapi.diff.impl;

import junit.framework.Test;

public class SingleTest {
  public static Test suite() {
    return new DiffFilesTest.MyIdeaTestCase("wrappingBug", ComparisonPolicy.IGNORE_SPACE){};
  }
}
