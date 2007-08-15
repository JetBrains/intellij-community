package com.intellij.execution.junit2;

import java.util.List;

public interface TestRoot extends TestProxyParent {
  List<TestProxy> getAllTests();
}
