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

import junit.framework.Assert;
import org.junit.Test;

/**
 * @author Sergey Simonchik
 */
public class CharArrayQueueTest {

  @org.junit.Test
  public void testSingleAdd() throws Exception {
    CharArrayQueue queue = new CharArrayQueue(0);
    char value = '1';
    queue.add(value);
    Assert.assertEquals(1, queue.size());
    Assert.assertEquals(value, queue.poll());
    Assert.assertEquals(-1, queue.poll());
  }

  @org.junit.Test
  public void testResize1() throws Exception {
    CharArrayQueue queue = new CharArrayQueue(4);
    char[] buf = new char[] {'1', '2', '3', '4', '5'};
    queue.addAll(buf);
    Assert.assertEquals(queue.size(), buf.length);
    for (char c : buf) {
      Assert.assertEquals(c, queue.poll());
    }
    Assert.assertEquals(-1, queue.poll());
  }

  @org.junit.Test
  public void testResize2() throws Exception {
    CharArrayQueue queue = new CharArrayQueue(4);
    char[] buf = new char[] {'1', '2', '3', '4', '5', '6', '7', '8'};
    queue.addAll(buf);
    Assert.assertEquals(queue.size(), buf.length);
    Assert.assertEquals(buf[0], queue.poll());
    Assert.assertEquals(buf[1], queue.poll());
    char v = '9';
    queue.add(v);
    queue.add(v);

    queue.addAll(buf); // array resize with myHead > myTail
    Assert.assertEquals(queue.size(), buf.length * 2);
    for (int i = 2; i < buf.length; i++) {
      Assert.assertEquals(buf[i], queue.poll());
    }
    Assert.assertEquals(v, queue.poll());
    Assert.assertEquals(v, queue.poll());
    for (char c : buf) {
      Assert.assertEquals(c, queue.poll());
    }
    Assert.assertEquals(-1, queue.poll());
  }

  @Test
  public void testAddString() throws Exception {
    CharArrayQueue queue = new CharArrayQueue(2);
    queue.add("");
    Assert.assertEquals(0, queue.size());
    queue.add("abc");
    Assert.assertEquals('a', queue.poll());
    Assert.assertEquals('b', queue.poll());
    Assert.assertEquals('c', queue.poll());
    Assert.assertEquals(-1, queue.poll());
  }
}
