package com.intellij.execution.junit2;

import com.intellij.execution.testframework.AbstractTestProxy;


public interface TestProxyListener {
  TestProxyListener[] EMPTY_ARRAY = new TestProxyListener[0];
  void onChildAdded(AbstractTestProxy parent, AbstractTestProxy newChild);
  void onChanged(AbstractTestProxy proxy);
  void onStatisticsChanged(AbstractTestProxy testProxy);
}
