/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import junit.framework.Assert;

import java.util.Iterator;

public class WeakListTest extends WeaksTestCase {
  private final com.intellij.util.containers.WeakReferenceArray myWeakArray = new com.intellij.util.containers.WeakReferenceArray();
  private final com.intellij.util.containers.WeakList myWeakList = new com.intellij.util.containers.WeakList(myWeakArray);

  protected void setUp() throws Exception {
    super.setUp();
    Assert.assertTrue(com.intellij.util.containers.WeakReferenceArray.PEFORM_CHECK_THREAD);
    com.intellij.util.containers.WeakReferenceArray.PEFORM_CHECK_THREAD = false;
  }

  protected void tearDown() throws Exception {
    com.intellij.util.containers.WeakReferenceArray.PEFORM_CHECK_THREAD = true;
    super.tearDown();
  }

  public void testCompresses() {
    for (int i = 0; i < 20; i++)
      addElement(new Object(), myWeakArray);
    Assert.assertEquals(20, myWeakList.size());
    checkSameElements(myWeakArray);
    String obj = "xxx";
    myHolder.add(obj);
    myWeakList.add(obj);
    checkSameElements(myWeakArray);
    myHolder.clear();
    gc();
    Assert.assertEquals(21, myWeakList.size());
    Assert.assertEquals(20, myWeakArray.getCorpseCount());
    myWeakList.add(new Object());
    Assert.assertTrue(String.valueOf(myWeakList.size()), myWeakList.size() < 20);
    gc();
    myHolder.add(obj);
    checkSameNotNulls(myWeakArray);
  }
  
  public void testIterator() {
    myWeakArray.add(new Object());
    addElement(new Object(), myWeakArray);
    myWeakArray.add(new Object());
    addElement(new Object(), myWeakArray);
    gc();
    Assert.assertEquals(2, myWeakArray.getAliveCount());
    int elementCount = 0;
    for (Iterator iterator = myWeakList.iterator(); iterator.hasNext();) {
      Object element = iterator.next();
      Assert.assertNotNull(element);
      elementCount++;
    }
    Assert.assertEquals(2, elementCount);
  }
  
  public void testRemoveViaIterator() {
    addElement(new Object(), myWeakArray);
    myWeakArray.add(new Object());
    addElement(new Object(), myWeakArray);
    myWeakArray.add(new Object());
    addElement(new Object(), myWeakArray);
    Iterator iterator = myWeakList.iterator();
    Assert.assertSame(myHolder.get(0), iterator.next());
    while (myHolder.get(1) != iterator.next());
    gc();
    Assert.assertEquals(4, myWeakArray.getAliveCount());
    iterator.remove();
    gc();
    Assert.assertEquals(3, myWeakArray.getAliveCount());
    iterator.next();
    gc();
    Assert.assertEquals(2, myWeakArray.getAliveCount());
    Assert.assertSame(myHolder.get(2), iterator.next());
    Assert.assertFalse(iterator.hasNext());
    myHolder.remove(1);
    checkSameNotNulls(myWeakArray);
    myWeakArray.compress(-1);
    checkSameElements(myWeakArray);
  }

  public void testRemoveLastViaIterator() {
    addElement(new Object(), myWeakArray);
    addElement(new Object(), myWeakArray);
    Iterator iterator = myWeakList.iterator();
    iterator.next();
    Assert.assertTrue(iterator.hasNext());
    iterator.next();
    Assert.assertFalse(iterator.hasNext());
    iterator.remove();
    Assert.assertFalse(iterator.hasNext());
    myHolder.remove(1);
    checkSameNotNulls(myWeakArray);
  }

  public void testIteratorKeepsFirstElement() {
    addElement(new Object(), myWeakArray);
    addElement(new Object(), myWeakArray);
    Iterator iterator = myWeakList.iterator();
    Assert.assertTrue(iterator.hasNext());
    myHolder.clear();
    gc();
    Assert.assertNotNull(iterator.next());
    Assert.assertFalse(iterator.hasNext());
  }
  
  public void testIteratorKeepsNextElement() {
    addElement(new Object(), myWeakArray);
    addElement(new Object(), myWeakArray);
    addElement(new Object(), myWeakArray);
    Iterator iterator = myWeakList.iterator();
    iterator.next();
    Assert.assertTrue(iterator.hasNext());
    myHolder.clear();
    gc();
    Assert.assertNotNull(iterator.next());
    Assert.assertFalse(iterator.hasNext());
  }
}
