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

package com.intellij.util.containers;

import com.intellij.openapi.util.Condition;
import com.intellij.util.WaitFor;
import junit.framework.TestCase;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public abstract class WeaksTestCase extends TestCase {
  protected final List<Object> myHolder = new ArrayList<Object>();
  protected WeakReferenceArray<Object> myCollection = new WeakReferenceArray<Object>();

  protected static void gc() {
    System.gc();
    System.gc();

    WeakReference<Object> weakReference = new WeakReference<Object>(new Object());
    do {
      System.gc();
    }
    while (weakReference.get() != null);
  }

  protected void checkSameElements(final Runnable action) {
    checkSameNotNulls(action);
    waitFor(new Condition<Void>() {
      @Override
      public boolean value(Void aVoid) {
        if (action != null) action.run();
        if (myHolder.size() != myCollection.size()) return false;
        for (int i = 0; i < myHolder.size(); i++) {
          if (myHolder.get(i) != myCollection.get(i)) return false;
        }
        WeakReference[] references = myCollection.getReferences();
        for (int i = myHolder.size(); i < references.length; i++) {
          if (references[i] != null) return false;
        }

        return true;
      }
    });
  }

  protected void checkSameNotNulls(final Runnable action) {
    waitFor(new Condition<Void>() {
      @Override
      public boolean value(Void aVoid) {
        if (action != null) action.run();
        int validIndex = -1;
        int validCount = 0;
        for (Object o : myHolder) {
          validIndex = nextValidIndex(validIndex, myCollection);
          if (o != myCollection.get(validIndex)) return false;
          validCount++;
        }
        if (myHolder.size() != validCount) return false;
        if (myCollection.size() != nextValidIndex(validIndex, myCollection)) return false;
        if (myCollection.size() - myHolder.size() != myCollection.getCorpseCount()) return false;

        //validIndex = Math.max(validIndex, 0);
        WeakReference[] references = myCollection.getReferences();
        for (int i = myCollection.size(); i < references.length; i++) {
          if (references[i] != null) return false;
        }
        return true;
      }
    });
  }

  private static int nextValidIndex(int validIndex, WeakReferenceArray collection) {
    validIndex++;
    while (validIndex < collection.size() && collection.get(validIndex) == null) validIndex++;
    return validIndex;
  }

  protected void addElement(Object o, WeakReferenceArray<Object> array) {
    myHolder.add(o);
    array.add(o);
  }

  protected void checkForAliveCount(final int expected) {
    waitFor(new Condition<Void>() {
      @Override
      public boolean value(Void aVoid) {
        return myCollection.getAliveCount() == expected;
      }

      @Override
      public String toString() {
        return "myCollection.getAliveCount() =" + myCollection.getAliveCount() + ";  expected=" + expected;
      }
    });
  }

  protected void checkForSize(final int expected, final boolean reduceCapacity) {
    waitFor(new Condition<Void>() {
      @Override
      public boolean value(Void aVoid) {
        if (reduceCapacity) {
          myCollection.reduceCapacity(-1);
        }
        return myCollection.size() == expected;
      }

      @Override
      public String toString() {
        return "Size: " + myCollection.size() + "; expected:" + expected;
      }
    });
  }

  private static void waitFor(final Condition<Void> condition) {
    new WaitFor(10000) {
      @Override
      protected boolean condition() {
        gc();
        return condition.value(null);
      }
    }.assertCompleted(condition.toString());
  }
}
