package com.intellij.execution.junit2;

import com.intellij.execution.testframework.AbstractTestProxy;

import java.util.ArrayList;

public class TestProxyListenersNotifier implements TestProxyListener {
  private ArrayList<TestProxyListener> myListeners;
  private TestProxyListener[] myCachedListeners;

  public void addListener(final TestProxyListener listener) {
    if (myListeners == null) myListeners = new ArrayList<TestProxyListener>();
    if (myListeners.contains(listener)) return;
    myListeners.add(listener);
    myCachedListeners = null;
  }

  public void onChanged(final AbstractTestProxy test) {
    final TestProxyListener[] listeners = getListeners();
    for (int i = 0; i < listeners.length; i++)
      listeners[i].onChanged(test);
  }

  public void onChildAdded(final AbstractTestProxy testProxy, final AbstractTestProxy child) {
    final TestProxyListener[] listeners = getListeners();
    for (int i = 0; i < listeners.length; i++)
      listeners[i].onChildAdded(testProxy, child);
  }

  private TestProxyListener[] getListeners() {
    if (myCachedListeners == null) {
      final int size = myListeners != null ? myListeners.size() : 0;
      myCachedListeners = size != 0 ? myListeners.toArray(new TestProxyListener[size]) : TestProxyListener.EMPTY_ARRAY;
    }
    return myCachedListeners;
  }

  public void onStatisticsChanged(final AbstractTestProxy test) {
    final TestProxyListener[] listeners = getListeners();
    for (int i = 0; i < listeners.length; i++) {
      final TestProxyListener listener = listeners[i];
      listener.onStatisticsChanged(test);
    }
  }
}
