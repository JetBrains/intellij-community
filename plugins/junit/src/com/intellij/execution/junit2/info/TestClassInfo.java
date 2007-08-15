package com.intellij.execution.junit2.info;

import com.intellij.execution.junit2.segments.ObjectReader;

class TestClassInfo extends ClassBasedInfo {
  public TestClassInfo() {
    super(DisplayTestInfoExtractor.FOR_CLASS);
  }

  public void readPacketFrom(final ObjectReader reader) {
    readClass(reader);
  }
}
