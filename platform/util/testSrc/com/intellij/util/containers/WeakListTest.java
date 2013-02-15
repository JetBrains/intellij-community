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

import java.util.Iterator;

public class WeakListTest extends WeaksTestCase {
  private final WeakList<Object> myWeakList = new WeakList<Object>(myCollection);

  public void testCompresses() {
    for (int i = 0; i < 20; i++) {
      addElement(new Object(), myCollection);
    }
    assertEquals(20, myWeakList.size());
    checkSameElements(null);
    String obj = "xxx";
    myHolder.add(obj);
    myWeakList.add(obj);
    checkSameElements(null);
    myHolder.clear();
    gc();
    assertEquals(21, myWeakList.size());
    checkForAliveCount(1); //obj is held here in the codeblock

    myWeakList.add(new Object());
    assertTrue(String.valueOf(myWeakList.size()), myWeakList.size() < 20);
    gc();
    myHolder.add(obj);
    checkSameNotNulls(null);
  }

  public void testClear() {
    for (int i = 0; i < 20; i++) {
      addElement(new Object(), myCollection);
    }
    assertEquals(20, myWeakList.size());
    myHolder.clear();
    gc();
    myWeakList.clear();
    assertFalse(myWeakList.iterator().hasNext());
  }
  
  public void testIterator() {
    myCollection.add(new Object());
    addElement(new Object(), myCollection);
    myCollection.add(new Object());
    addElement(new Object(), myCollection);
    gc();
    checkForAliveCount(2);
    int elementCount = 0;
    for (Object element : myWeakList) {
      assertNotNull(element);
      elementCount++;
    }
    assertEquals(2, elementCount);
  }
  
  public void testRemoveViaIterator() {
    addElement(new Object(), myCollection);
    myCollection.add(new Object());
    addElement(new Object(), myCollection);
    myCollection.add(new Object());
    addElement(new Object(), myCollection);
    Iterator<Object> iterator = myWeakList.iterator();
    assertSame(myHolder.get(0), iterator.next());
    while (myHolder.get(1) != iterator.next());
    gc();
    checkForAliveCount(4);
    iterator.remove();
    gc();
    checkForAliveCount(3);
    iterator.next();
    gc();
    checkForAliveCount(2);
    assertSame(myHolder.get(2), iterator.next());
    assertFalse(iterator.hasNext());
    myHolder.remove(1);
    checkSameNotNulls(null);
    checkSameElements(new Runnable() {
      @Override
      public void run() {
        myCollection.compress(-1);
      }
    });
  }

  public void testRemoveLastViaIterator() {
    addElement(new Object(), myCollection);
    addElement(new Object(), myCollection);
    Iterator<Object> iterator = myWeakList.iterator();
    iterator.next();
    assertTrue(iterator.hasNext());
    iterator.next();
    assertFalse(iterator.hasNext());
    iterator.remove();
    assertFalse(iterator.hasNext());
    myHolder.remove(1);
    checkSameNotNulls(null);
  }

  public void testIteratorKeepsFirstElement() {
    addElement(new Object(), myCollection);
    addElement(new Object(), myCollection);
    Iterator<Object> iterator = myWeakList.iterator();
    assertTrue(iterator.hasNext());
    myHolder.clear();
    gc();
    assertNotNull(iterator.next());
    assertFalse(iterator.hasNext());
  }
  
  public void testIteratorKeepsNextElement() {
    addElement(new Object(), myCollection);
    addElement(new Object(), myCollection);
    addElement(new Object(), myCollection);
    Iterator<Object> iterator = myWeakList.iterator();
    iterator.next();
    assertTrue(iterator.hasNext());
    myHolder.clear();
    gc();
    assertNotNull(iterator.next());
    assertFalse(iterator.hasNext());
  }
}
