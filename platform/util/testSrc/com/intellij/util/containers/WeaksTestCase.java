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

import junit.framework.TestCase;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public abstract class WeaksTestCase extends TestCase {
  protected static final boolean JVM_IS_GC_CAPABLE = isJvmGcCapable();
  private static boolean isJvmGcCapable() {
    List<Object> list = new ArrayList<Object>();
    Object o = new Object();
    list.add(o);
    WeakReference<Object> wr = new WeakReference<Object>(o);
    assertSame(o, wr.get());
    o = null;
    list.clear();
    gc();
    boolean cleared = wr.get() == null;
    if (!cleared) {
      System.out.println("GC INCAPABLE JVM DETECTED");
    }
    return cleared;
  }


  protected final List<Object> myHolder = new ArrayList<Object>();

  protected static void gc() {
    System.gc();
    try {
      List<Object> list = new ArrayList<Object>();
      while (Runtime.getRuntime().freeMemory() > 10000) {
        list.add(new byte[1000]);
      }

      System.gc();
      list = null;
    }
    catch (OutOfMemoryError e) {
      // ignore
    }
    System.gc();

    WeakReference<Object> weakReference = new WeakReference<Object>(new Object());
    do {
      System.gc();
    }
    while (weakReference.get() != null);
  }


  protected void checkSameElements(WeakReferenceArray collection) {
    checkSameNotNulls(collection);
    assertEquals(myHolder.size(), collection.size());
    for (int i = 0; i < myHolder.size(); i++) {
      assertSame(myHolder.get(i), collection.get(i));
    }
    WeakReference[] references = collection.getReferences();
    for (int i = myHolder.size(); i < references.length; i++)
      assertNull(references[i]);
  }

  protected void checkSameNotNulls(WeakReferenceArray collection) {
    int validIndex = -1;
    int validCount = 0;
    for (int i = 0; i < myHolder.size(); i++) {
      validIndex = nextValidIndex(validIndex, collection);
      assertSame(i + "==" + validIndex, myHolder.get(i), collection.get(validIndex));
      validCount++;
    }
    assertEquals(myHolder.size(), validCount);
    assertTrue(collection.size() >= nextValidIndex(validIndex, collection));
    assertTrue(collection.size() - myHolder.size() >= collection.getCorpseCount());

    //validIndex = Math.max(validIndex, 0);
    WeakReference[] references = collection.getReferences();
    for (int i = collection.size(); i < references.length; i++)
      assertNull(references[i]);
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
}
