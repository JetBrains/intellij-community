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

import java.lang.ref.WeakReference;

public class WeakReferenceArrayTest extends WeaksTestCase {
  public void testCorpseCounter() {
    addElements(5);
    checkForAliveCount(5);
    checkSameElements(null);
    myHolder.remove(3);
    gc();
    checkForAliveCount(4);
    myCollection.remove(3);
    checkSameNotNulls(null);
    checkForAliveCount(4);
    myCollection.remove(2);
    checkForAliveCount(3);
    myHolder.remove(2);
    gc();
    checkSameNotNulls(null);
    checkForAliveCount(3);
  }

  public void testAddRemove() {
    myHolder.add("1");
    myHolder.add("2");
    checkForSize(0, false);

    myCollection.add(myHolder.get(0));
    checkForSize(1, false);
    myCollection.add(myHolder.get(1));
    checkSameElements(null);
    assertSame(myHolder.get(0), myCollection.remove(0));
    checkForSize(2, false);
    assertNull(myCollection.remove(0));
  }

  public void testRemoveDead() {
    myCollection.add(new Object());
    myCollection.add(this);
    gc();
    checkForSize(2, false);
    checkForAliveCount(1);

    assertNull(myCollection.remove(0));
    checkForAliveCount(1);
  }

  public void testCompress() {
    addElements(5);
    checkSameElements(null);
    myHolder.remove(0);
    myHolder.remove(0);
    checkSameElements(new Runnable() {
      @Override
      public void run() {
        myCollection.compress(-1);
      }
    });
    checkForAliveCount(3);
  }

  public void testCompressTrackingLastSurvived() {
    myCollection.add(new Object());
    addElement(new Object());
    myCollection.add(new Object());
    addElement(new Object());
    myCollection.add(new Object());
    gc();
    checkSameElements(new Runnable() {
      @Override
      public void run() {
        myCollection.compress(-1);
      }
    });
  }

  public void testAddingManyElement() {
    int initialCapacity = fullCollection();
    assertEquals(initialCapacity, myCollection.getCapacity());
    assertTrue(initialCapacity > 3); // Need to go on
    myHolder.remove(0);
    myHolder.remove(0);
    gc();
    checkSameNotNulls(null);
    addElement(new Object());
    assertEquals(initialCapacity, myCollection.getCapacity());
    checkSameElements(null);
  }

  public void testReduceSize() {
    int initialCapacity = fullCollection();
    assertEquals(initialCapacity, myCollection.getCapacity());
    addElement(new Object());
    int grownCapacity = 2 * initialCapacity;
    assertEquals(grownCapacity, myCollection.getCapacity());
    checkSameElements(null);
    myCollection.reduceCapacity(-1);
    assertEquals(grownCapacity, myCollection.getCapacity());
    myHolder.remove(0);
    gc();
    myCollection.reduceCapacity(-1);
    assertEquals(grownCapacity, myCollection.getCapacity());
    myHolder.remove(0);
    for (int i = 0; i < grownCapacity; i++) addElement(new Object());
    for (int i = 0; i < grownCapacity; i++) myHolder.remove(0);
    final int lastCapacity = myCollection.getCapacity();
    gc();
    checkSameNotNulls(null);
    myCollection.reduceCapacity(-1);
    assertTrue(lastCapacity > myCollection.getCapacity());
    checkSameElements(new Runnable() {
      @Override
      public void run() {
        myCollection.reduceCapacity(-1);
      }
    });
    myHolder.clear();
    gc();
    checkForSize(0, true);
    addElement(new Object());
    checkSameElements(null);
  }

  public void testReduceCapacityOfAliveCollection() {
    myCollection = new WeakReferenceArray<Object>(WeakReferenceArray.MINIMUM_CAPACITY * 3);
    addElement(new Object());
    myCollection.reduceCapacity(-1);
    checkForAliveCount(1);
    checkSameElements(new Runnable() {
      @Override
      public void run() {
        myCollection.reduceCapacity(-1);
      }
    });
  }

  public void testRemoveAlives() {
    addElement(new Object());
    addElement(new Object());
    addElement(new Object());
    WeakReference[] references = copyReferences();
    assertTrue(references.length >= 3);
    myCollection.remove(1);
    myCollection.remove(0);
    checkForAliveCount(1);
    myHolder.remove(0);
    myHolder.remove(0);
    checkSameNotNulls(null);
    gc();
    checkForAliveCount(1);
  }

  private WeakReference[] copyReferences() {
    WeakReference[] references = new WeakReference[myCollection.getCapacity()];
    System.arraycopy(myCollection.getReferences(), 0, references, 0, references.length);
    return references;
  }

  public void testRemoveEnqueuedReference() {
    addElement(new Object());
    WeakReference reference = myCollection.getReferences()[0];
    assertSame(reference.get(), myHolder.get(0));
    myHolder.clear();
    reference.enqueue();
    myCollection.remove(0);
    gc();
    checkForAliveCount(0);
    myCollection.getCorpseCount();
    reference = null;
    gc();
    checkForAliveCount(0);
  }

  public void testRemoveNotEnqueuedReference() {
    addElement(new Object());
    WeakReference reference = myCollection.getReferences()[0];
    myCollection.remove(0);
    gc();
    myHolder.clear();
    reference.enqueue();
    gc();
    checkForAliveCount(0);
  }

  public void testOutofboundsReferences() {
    for (int i = 0; i < 400; i++) addElement(new Object());
    WeakReference[] references = copyReferences();
    for (int i = 0; i < 100; i++) references[i].clear();
    for (int i = 100; i < 395; i++) myCollection.remove(i);
    myCollection.reduceCapacity(-1);
    assertEquals(210, myCollection.getCapacity());
    checkForSize(5, true);
    checkForAliveCount(5);
    for (int i = 0; i < 100; i++) assertFalse(String.valueOf(i), references[i].enqueue());

    for (int i = 0; i < myCollection.getCapacity() - 20; i++) addElement(new Object());
    for (int i = 0; i < myCollection.getCapacity() - 20; i++) myCollection.remove(i);
    myCollection.reduceCapacity(-1);
    for (int i = 0; i < 100; i++) assertFalse(references[i].enqueue());
    myCollection.reduceCapacity(-1);
  }

  public void testRemoveAliveReference() {
    Object obj = new Object();
    myCollection.add(obj);
    WeakReference[] references = copyReferences();
    assertSame(obj, references[0].get());
    assertTrue(myCollection.removeReference(0));
    assertTrue(references[0].isEnqueued());
    assertNull(references[0].get());
    checkForAliveCount(0);
    assertNull(myCollection.getReferences()[0]);
  }

  public void testRemoveDeadReference() {
    Object obj = new Object();
    myCollection.add(obj);
    WeakReference[] references = copyReferences();
    references[0].clear();
    references[0].enqueue();
    assertFalse(myCollection.removeReference(0));
    checkForAliveCount(0);
    assertNull(myCollection.getReferences()[0]);
  }

  public void testRemoveRemovedReference() {
    Object obj = new Object();
    myCollection.add(obj);
    myCollection.remove(0);
    checkForAliveCount(0);
    assertFalse(myCollection.removeReference(0));
    checkForAliveCount(0);
  }

  public void testCompacting() {
    addElements(20);
    removeEven(1);
    checkSameElements(new Runnable() {
      @Override
      public void run() {
        myCollection.reduceCapacity(-1);
      }
    });

    addElements(19);
    removeEven(1);

    checkSameElements(new Runnable() {
      @Override
      public void run() {
        myCollection.compress(-1);
      }
    });

    addElements(19);
    removeEven(0);
    checkSameElements(new Runnable() {
      @Override
      public void run() {
        myCollection.reduceCapacity(-1);
      }
    });

    addElements(19);
    removeEven(0);
    checkSameElements(new Runnable() {
      @Override
      public void run() {
        myCollection.compress(-1);
      }
    });
  }

  private void removeEven(int remainder) {
    for (int i = 0; i < myCollection.size(); i++) {
      if (i % 4 != remainder) myHolder.remove(myCollection.remove(i));
    }
  }

  private int fullCollection() {
    int initialCapacity = myCollection.getCapacity();
    for (int i = 0; i < initialCapacity; i++) {
      addElement(new Object());
    }
    return initialCapacity;
  }

  private void addElements(int count) {
    for (int i = 0; i < count; i++) {
      addElement(new Object());
    }
  }

  private void addElement(Object o) {
    addElement(o, myCollection);
  }

}
