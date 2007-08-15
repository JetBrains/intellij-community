package com.intellij.execution.junit2.segments;

import com.intellij.execution.junit2.TestProxy;
import com.intellij.rt.execution.junit.segments.PoolOfDelimiters;

public class ObjectReader extends SegmentReader {
  private final InputObjectRegistry myObjectRegistry;

  public ObjectReader(final String packet, final int offset, final InputObjectRegistry registry) {
    super(packet.substring(offset, packet.length()));
    myObjectRegistry = registry;
  }

  public TestProxy readObject() {
    return myObjectRegistry.getByKey(upTo(PoolOfDelimiters.REFERENCE_END));
  }
}
