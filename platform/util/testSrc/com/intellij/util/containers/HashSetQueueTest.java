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

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;

public class HashSetQueueTest extends TestCase {
  private final HashSetQueue<String> myQueue = new HashSetQueue<>();

  private static final int[] primeCapacities = {
    3, 5, 7, 11, 17, 23, 31, 37, 43, 47, 67, 79, 89, 97, 137, 163, 179, 197, 277, 311, 331, 359, 379, 397, 433, 557,
    599, 631, 673, 719, 761, 797, 877, 953, 1039, 1117, 1201, 1277, 1361, 1439, 1523, 1597, 1759, 1907, 2081, 2237,
    2411, 2557, 2729, 2879, 3049, 3203, 3527, 3821, 4177, 4481, 4831, 5119, 5471, 5779, 6101, 6421, 7057, 7643, 8363,
    8963, 9677, 10243, 10949, 11579, 12203, 12853, 14143, 15287, 16729, 17929, 19373, 20507, 21911, 23159, 24407,
    25717, 28289, 30577, 33461, 35863, 38747, 41017, 43853, 46327, 48817, 51437, 56591, 61169, 66923, 71741, 77509,
    82037, 87719, 92657, 97649, 102877, 113189, 122347, 133853, 143483, 155027, 164089, 175447, 185323, 195311, 205759,
    226379, 244703, 267713, 286973, 310081, 328213, 350899, 370661, 390647, 411527, 452759, 489407, 535481, 573953,
    620171, 656429, 701819, 741337, 781301, 823117, 905551, 978821, 1070981, 1147921, 1240361, 1312867, 1403641,
    1482707, 1562611, 1646237, 1811107, 1957651, 2141977, 2295859, 2480729, 2625761, 2807303, 2965421, 3125257,
    3292489, 3622219, 3915341, 4283963, 4591721, 4961459, 5251529, 5614657, 5930887, 6250537, 6584983, 7244441,
    7830701, 8567929, 9183457, 9922933, 10503061, 11229331, 11861791, 12501169, 13169977, 14488931, 15661423,
    17135863, 18366923, 19845871, 21006137, 22458671, 23723597, 25002389, 26339969, 28977863, 31322867, 34271747,
    36733847, 39691759, 42012281, 44917381, 47447201, 50004791, 52679969, 57955739, 62645741, 68543509, 73467739,
    79383533, 84024581, 89834777, 94894427, 100009607, 105359939, 115911563, 125291483, 137087021, 146935499,
    158767069, 168049163, 179669557, 189788857, 200019221, 210719881, 231823147, 250582987, 274174111, 293871013,
    317534141, 336098327, 359339171, 379577741, 400038451, 421439783, 463646329, 501165979, 548348231, 587742049,
    635068283, 672196673, 718678369, 759155483, 800076929, 842879579, 927292699, 1002331963, 1096696463, 1175484103,
    1270136683, 1344393353, 1437356741, 1518310967, 1600153859, 1685759167, 1854585413, 2004663929, 2147483647
  };

  public static int nextPrime(int desiredCapacity) {
    int i = Arrays.binarySearch(primeCapacities, desiredCapacity);
    if (i < 0) {
      // desired capacity not found, choose next prime greater
      // than desired capacity
      i = -i - 1; // remember the semantics of binarySearch...
    }
    return primeCapacities[i];
  }

  public void testEmpty() {
    assertEquals(0, myQueue.size());
    assertTrue(myQueue.isEmpty());
    assertThat(myQueue).isEmpty();
  }

  public void testAddingGetting() {
    String element = "1";
    myQueue.add(element);
    assertEquals(1, myQueue.size());
    assertFalse(myQueue.isEmpty());
    assertThat(myQueue).containsExactly(element);
    assertSame(element, myQueue.poll());
    testEmpty();
    myQueue.add("2");
    assertEquals(1, myQueue.size());
    myQueue.add("3");
    assertEquals(2, myQueue.size());
    assertThat(new ArrayList<>(myQueue)).containsExactly("2", "3");
    assertEquals("2", myQueue.poll());
    assertEquals("3", myQueue.poll());
    testEmpty();
  }

  public void testQuibble() {
    String xxx = "xxx";
    myQueue.add(xxx);
    assertThat(myQueue.toArray()).containsExactly(xxx);
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

    int delta = nextPrime(N);
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
    assertThat(position).isLessThan(nextPos);
    assertThat(position).isEqualByComparingTo(position);
    assertThat(nextPos).isGreaterThan(position);
    assertSame(o, nextPos.peek());

    assertNull(nextPos.next());

    HashSetQueue.PositionalIterator.IteratorPosition<String> nextPos2 = position.next();
    assertThat(nextPos2).isEqualByComparingTo(nextPos);
    assertSame(o, nextPos2.peek());

    assertTrue(iterator.hasNext());
    assertSame(o, iterator.next());
    assertSame(o, nextPos.peek());
    assertNull(nextPos.next());
  }
}
