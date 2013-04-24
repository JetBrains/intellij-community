package com.intellij.util.containers;

import com.intellij.openapi.util.LowMemoryWatcher;
import jsr166e.SequenceLock;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.locks.Lock;

/**
* User: Maxim.Mossienko
* Date: 2/4/13
* Time: 4:50 PM
*/
public class RecentStringInterner {
  private final int myStripeMask;
  private final SLRUCache<String, String>[] myInterns;
  private final Lock[] myStripeLocks;
  // LowMemoryWatcher relies on field holding it
  @SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
  private final LowMemoryWatcher myLowMemoryWatcher;

  public RecentStringInterner() {
    this(8192);
  }

  public RecentStringInterner(int capacity) {
    final int stripes = 16;
    //noinspection unchecked
    myInterns = new SLRUCache[stripes];
    myStripeLocks = new Lock[myInterns.length];
    for(int i = 0; i < myInterns.length; ++i) {
      myInterns[i] = new SLRUCache<String, String>(capacity / stripes, capacity / stripes) {
        @NotNull
        @Override
        public String createValue(String key) {
          return key;
        }

        @Override
        protected void putToProtectedQueue(String key, String value) {
          super.putToProtectedQueue(value, value);
        }
      };
      myStripeLocks[i] = new SequenceLock();
    }

    assert Integer.highestOneBit(stripes) == stripes;
    myStripeMask = stripes - 1;
    myLowMemoryWatcher = LowMemoryWatcher.register(new Runnable() {
      @Override
      public void run() {
        clear();
      }
    });
  }

  public String get(String s) {
    if (s == null) return null;
    final int stripe = Math.abs(s.hashCode()) & myStripeMask;
    try {
      myStripeLocks[stripe].lock();
      return myInterns[stripe].get(s);
    } finally {
      myStripeLocks[stripe].unlock();
    }
  }

  public void clear() {
    for(int i = 0; i < myInterns.length; ++i) {
      myStripeLocks[i].lock();
      myInterns[i].clear();
      myStripeLocks[i].unlock();
    }
  }
}
