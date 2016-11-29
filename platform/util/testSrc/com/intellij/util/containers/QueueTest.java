/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import junit.framework.TestCase;

public class QueueTest extends TestCase {
  private final Assertion CHECK = new Assertion();
  private final Queue<String> myQueue = new Queue<>(1);

  public void testEmpty() {
    assertEquals(0, myQueue.size());
    assertTrue(myQueue.isEmpty());
    CHECK.empty(myQueue.toList());
  }

  public void testAddingGetting() {
    String element = "1";
    myQueue.addLast(element);
    assertEquals(1, myQueue.size());
    assertFalse(myQueue.isEmpty());
    CHECK.singleElement(myQueue.toList(), element);
    assertSame(element, myQueue.pullFirst());
    testEmpty();
    myQueue.addLast("2");
    assertEquals(1, myQueue.size());
    myQueue.addLast("3");
    assertEquals(2, myQueue.size());
    CHECK.compareAll(new Object[]{"2", "3"}, myQueue.toList());
    assertEquals("2", myQueue.pullFirst());
    assertEquals("3", myQueue.pullFirst());
    testEmpty();
  }

  public void testQuibble() {
    Queue<String> queue = new Queue<>(0);
    String xxx = "xxx";
    queue.addLast(xxx);
    CHECK.compareAll(queue.toArray(), new String[] {xxx});
    Object x = queue.pullFirst();
    assertEquals(xxx,x);
    assertTrue(queue.isEmpty());
  }

  public void testRemoving() {
    Queue<Object> queue = new Queue<>(10);
    for (int i = 0; i < 9; i++) {
      queue.addLast(String.valueOf(i));
    }
    for (int i = 0; i < 4; i++) {
      Object first = queue.pullFirst();      
      queue.addLast(first);
    }
    for (int i = 3; i >= 0; i--) {
      assertEquals(String.valueOf(i), queue.removeLast());
    }
    for (int i = 8; i >= 6; i--) {
      assertEquals(String.valueOf(i), queue.removeLast());
    }
    for (int i = 4; i <= 5; i++) {
      assertEquals(String.valueOf(i), queue.pullFirst());
    }
  }

  public void testSetting() {
    Queue<Object> queue = new Queue<>(10);
    for (int i = 0; i < 9; i++) {
      queue.addLast(String.valueOf(i));
    }
    for (int i = 0; i < 4; i++) {
      Object first = queue.pullFirst();
      queue.addLast(first);
    }

    assertEquals(String.valueOf(4), queue.peekFirst());
    for (int i = 0; i < 5; i++) {
      assertEquals(String.valueOf(i + 4), queue.set(i, String.valueOf(i*i)));
    }
    for (int i = 0; i < 4; i++) {
      int k = i + 5;
      assertEquals(String.valueOf(i), queue.set(k, String.valueOf(k*k)));
    }
    for (int i = 0; i < 9; i++) {
      assertEquals(String.valueOf(i*i), queue.pullFirst());
    }
  }


  public void testCycling() {
    Queue<Object> queue = new Queue<>(10);
    for (int i = 0; i < 9; i++) {
      queue.addLast(String.valueOf(i));
      assertEquals(i+1, queue.size());
    }
    CHECK.count(9, queue.toList());
    for (int i = 0; i < 9; i++) {
      Object first = queue.pullFirst();
      assertEquals(String.valueOf(i), first);
      assertEquals(8, queue.size());
      queue.addLast(first);
    }
    for (int i = 0; i < 9; i++) {
      assertEquals(String.valueOf(i), queue.pullFirst());
      assertEquals(8 - i, queue.size());
    }
  }
}
