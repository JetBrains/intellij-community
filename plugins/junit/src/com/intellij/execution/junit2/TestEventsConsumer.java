package com.intellij.execution.junit2;

public interface TestEventsConsumer {
  void onEvent(TestEvent event);
}
