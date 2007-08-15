package com.intellij.execution.junit2.info;

import com.intellij.execution.junit2.segments.ObjectReader;
import com.intellij.execution.ExecutionBundle;

class DefaultTestInfo extends ClassBasedInfo {
  public DefaultTestInfo() {
    super(DisplayTestInfoExtractor.CLASS_FULL_NAME);
  }

  public void readPacketFrom(final ObjectReader reader) {
    reader.readInt(); //TODO remove test count from packet
    readClass(reader);
  }

  public String getName() {
    return ExecutionBundle.message("test.cases.count.message", getTestsCount());
  }
}
