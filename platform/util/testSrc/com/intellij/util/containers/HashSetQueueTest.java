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
import gnu.trove.PrimeFinder;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Iterator;

public class HashSetQueueTest extends TestCase {
  private final Assertion CHECK = new Assertion();
  private final HashSetQueue<String> myQueue = new HashSetQueue<>();

  public void testEmpty() {
    assertEquals(0, myQueue.size());
    assertTrue(myQueue.isEmpty());
    CHECK.empty(myQueue);
  }

  public void testAddingGetting() {
    String element = "1";
    myQueue.add(element);
    assertEquals(1, myQueue.size());
    assertFalse(myQueue.isEmpty());
    CHECK.singleElement(myQueue, element);
    assertSame(element, myQueue.poll());
    testEmpty();
    myQueue.add("2");
    assertEquals(1, myQueue.size());
    myQueue.add("3");
    assertEquals(2, myQueue.size());
    CHECK.compareAll(new Object[]{"2", "3"}, new ArrayList<>(myQueue));
    assertEquals("2", myQueue.poll());
    assertEquals("3", myQueue.poll());
    testEmpty();
  }

  public void testQuibble() {
    String xxx = "xxx";
    myQueue.add(xxx);
    CHECK.compareAll(myQueue.toArray(), new String[] {xxx});
    Object x = myQueue.poll();
    assertEquals(xxx,x);
    assertTrue(myQueue.isEmpty());
  }

  public void testRemoving() {
    for (int i = 0; i < 9; i++) {
      myQueue.add(String.valueOf(i));
    }
    for (int i = 0; i < 4; i++) {
      String first = myQueue.poll();
      myQueue.add(first);
    }
    for (int i = 4; i <= 5; i++) {
      assertEquals(String.valueOf(i), myQueue.poll());
    }
  }

  public void testAddTwice() {
    assertTrue(myQueue.add("1"));
    assertFalse(myQueue.add("1"));
    assertEquals(1, myQueue.size());
  }

  public void testOrder() {
    int N = 10000;
    for (int i = 0; i < N; i++) {
      String toAdd = String.valueOf(i);
      assertTrue(myQueue.add(toAdd));
      assertEquals(i+1, myQueue.size());
      String toLookup = String.valueOf(i);
      assertNotSame(toLookup, toAdd);
      String found = myQueue.find(toLookup);
      assertSame(toAdd, found);
      assertTrue(myQueue.contains(toAdd));
      assertTrue(myQueue.contains(toLookup));

      int it=0;
      for (String s : myQueue) {
        String str = String.valueOf(it);
        assertEquals(str, s);
        assertTrue(myQueue.contains(str));
        it++;
      }
      assertEquals(i+1, it);
    }

    int delta = PrimeFinder.nextPrime(N);
    int toRemove = 0;
    for (int i = 0; i < N; i++) {
      String str = String.valueOf(i);
      assertTrue(myQueue.contains(str));
      boolean removed = myQueue.remove(str);
      assertTrue(removed);
      assertEquals(N-i-1, myQueue.size());
      assertFalse(myQueue.contains(str));

      toRemove = (toRemove + delta) % N;
    }
  }

  public void testIteratorCatchesUpQueueModificationImmediately() {
    assertTrue(myQueue.add("1"));
    Iterator<String> iterator = myQueue.iterator();
    assertTrue(iterator.hasNext());
    assertEquals("1", iterator.next());
    assertFalse(iterator.hasNext());

    myQueue.add("2");
    assertTrue(iterator.hasNext());
    assertEquals("2", iterator.next());
    assertFalse(iterator.hasNext());
  }

  public void testIterator() {
    assertTrue(myQueue.add("1"));
    Iterator<String> iterator = myQueue.iterator();
    assertTrue(iterator.hasNext());
    assertEquals("1", iterator.next());
    assertFalse(iterator.hasNext());
  }

  public void testIteratorPosition() {
    String o = "1";
    assertTrue(myQueue.add(o));
    HashSetQueue.PositionalIterator<String> iterator = myQueue.iterator();
    HashSetQueue.PositionalIterator.IteratorPosition<String> position = iterator.position();
    try {
      position.peek();
      fail("Must have thrown ISE");
    }
    catch (IllegalStateException ignored) {
    }

    HashSetQueue.PositionalIterator.IteratorPosition<String> nextPos = position.next();
    assertTrue(position.compareTo(nextPos) < 0);
    assertTrue(position.compareTo(position) == 0);
    assertTrue(nextPos.compareTo(position) > 0);
    assertSame(o, nextPos.peek());

    assertNull(nextPos.next());

    HashSetQueue.PositionalIterator.IteratorPosition<String> nextPos2 = position.next();
    assertTrue(nextPos2.compareTo(nextPos) == 0);
    assertSame(o, nextPos2.peek());

    assertTrue(iterator.hasNext());
    assertSame(o, iterator.next());
    assertSame(o, nextPos.peek());
    assertNull(nextPos.next());
  }
}
