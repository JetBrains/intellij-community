/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
