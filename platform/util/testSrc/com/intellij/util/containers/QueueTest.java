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

import com.intellij.util.Assertion;
import junit.framework.Assert;
import junit.framework.TestCase;

public class QueueTest extends TestCase {
  private final Assertion CHECK = new Assertion();
  private final com.intellij.util.containers.Queue myQueue = new com.intellij.util.containers.Queue(1);

  public void testEmpty() {
    Assert.assertEquals(0, myQueue.size());
    Assert.assertTrue(myQueue.isEmpty());
    CHECK.empty(myQueue.toList());
  }

  public void testAddingGetting() {
    String element = "1";
    myQueue.addLast(element);
    Assert.assertEquals(1, myQueue.size());
    Assert.assertFalse(myQueue.isEmpty());
    CHECK.singleElement(myQueue.toList(), element);
    Assert.assertSame(element, myQueue.pullFirst());
    testEmpty();
    myQueue.addLast("2");
    Assert.assertEquals(1, myQueue.size());
    myQueue.addLast("3");
    Assert.assertEquals(2, myQueue.size());
    CHECK.compareAll(new Object[]{"2", "3"}, myQueue.toList());
    Assert.assertEquals("2", myQueue.pullFirst());
    Assert.assertEquals("3", myQueue.pullFirst());
    testEmpty();
  }

  public void testQuibble() {
    com.intellij.util.containers.Queue queue = new com.intellij.util.containers.Queue(0);
    String xxx = "xxx";
    queue.addLast(xxx);
    CHECK.compareAll(queue.toArray(), new String[] {xxx});
    Object x = queue.pullFirst();
    assertEquals(xxx,x);
    assertTrue(queue.isEmpty());
  }

  public void testRemoving() {
    com.intellij.util.containers.Queue queue = new com.intellij.util.containers.Queue(10);
    for (int i = 0; i < 9; i++) {
      queue.addLast(String.valueOf(i));
    }
    for (int i = 0; i < 4; i++) {
      Object first = queue.pullFirst();      
      queue.addLast(first);
    }
    for (int i = 3; i >= 0; i--) {
      Assert.assertEquals(String.valueOf(i), queue.removeLast());
    }
    for (int i = 8; i >= 6; i--) {
      Assert.assertEquals(String.valueOf(i), queue.removeLast());
    }
    for (int i = 4; i <= 5; i++) {
      Assert.assertEquals(String.valueOf(i), queue.pullFirst());
    }
  }

  public void testSetting() {
    com.intellij.util.containers.Queue queue = new com.intellij.util.containers.Queue(10);
    for (int i = 0; i < 9; i++) {
      queue.addLast(String.valueOf(i));
    }
    for (int i = 0; i < 4; i++) {
      Object first = queue.pullFirst();
      queue.addLast(first);
    }

    Assert.assertEquals(String.valueOf(4), queue.peekFirst());
    for (int i = 0; i < 5; i++) {
      Assert.assertEquals(String.valueOf(i + 4), queue.set(i, String.valueOf(i*i)));
    }
    for (int i = 0; i < 4; i++) {
      int k = i + 5;
      Assert.assertEquals(String.valueOf(i), queue.set(k, String.valueOf(k*k)));
    }
    for (int i = 0; i < 9; i++) {
      Assert.assertEquals(String.valueOf(i*i), queue.pullFirst());
    }
  }


  public void testCycling() {
    com.intellij.util.containers.Queue queue = new com.intellij.util.containers.Queue(10);
    for (int i = 0; i < 9; i++) {
      queue.addLast(String.valueOf(i));
      Assert.assertEquals(i+1, queue.size());
    }
    CHECK.count(9, queue.toList());
    for (int i = 0; i < 9; i++) {
      Object first = queue.pullFirst();
      Assert.assertEquals(String.valueOf(i), first);
      Assert.assertEquals(8, queue.size());
      queue.addLast(first);
    }
    for (int i = 0; i < 9; i++) {
      Assert.assertEquals(String.valueOf(i), queue.pullFirst());
      Assert.assertEquals(8 - i, queue.size());
    }

  }
}
