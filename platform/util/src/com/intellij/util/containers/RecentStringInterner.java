/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.util.containers;

import com.intellij.openapi.util.LowMemoryWatcher;
import jsr166e.extra.SequenceLock;
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
      try {
        myStripeLocks[i].lock();
        myInterns[i].clear();
      }
      finally {
        myStripeLocks[i].unlock();
      }
    }
  }
}
