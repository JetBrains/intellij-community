package com.intellij.execution.junit2.segments;

import com.intellij.execution.junit2.TestProxy;

public interface InputObjectRegistry extends PacketConsumer {
  TestProxy getByKey(String key);
}
