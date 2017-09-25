/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import org.junit.Assert;
import org.junit.Test;

public class CircularCharBufferTest {
  @Test
  public void testSingleAdd() {
    CircularCharBuffer queue = new CircularCharBuffer(0);
    char value = '1';
    queue.add(value);
    Assert.assertEquals(1, queue.size());
    Assert.assertEquals(value, queue.poll());
    Assert.assertEquals(-1, queue.poll());
  }

  @Test
  public void testResize1() {
    CircularCharBuffer queue = new CircularCharBuffer(4);
    char[] buf = {'1', '2', '3', '4', '5'};
    queue.add(buf);
    Assert.assertEquals(queue.size(), buf.length);
    for (char c : buf) {
      Assert.assertEquals(c, queue.poll());
    }
    Assert.assertEquals(-1, queue.poll());
  }

  @Test
  public void testResize2() {
    CircularCharBuffer queue = new CircularCharBuffer(4, 16);
    char[] buf = {'1', '2', '3', '4', '5', '6', '7', '8'};
    queue.add(buf);
    Assert.assertEquals(queue.size(), buf.length);
    Assert.assertEquals(buf[0], queue.poll());
    Assert.assertEquals(buf[1], queue.poll());
    char v = '9';
    queue.add(v);
    queue.add(v);

    queue.add(buf); // array resize with myHead > myTail
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
  public void testAddString() {
    CircularCharBuffer queue = new CircularCharBuffer(2, 16);
    queue.add("");
    Assert.assertEquals(0, queue.size());
    queue.add("abc");
    Assert.assertEquals('a', queue.poll());
    Assert.assertEquals('b', queue.poll());
    Assert.assertEquals('c', queue.poll());
    Assert.assertEquals(-1, queue.poll());
  }

  @Test
  public void testOverflow() {
    CircularCharBuffer queue = new CircularCharBuffer(1, 4);
    queue.add("1");
    Assert.assertEquals("1", queue.getText());
    queue.add("2");
    Assert.assertEquals("12", queue.getText());
    queue.add("3");
    Assert.assertEquals("123", queue.getText());
    queue.add("4");
    Assert.assertEquals("1234", queue.getText());
    queue.add("5");
    Assert.assertEquals("2345", queue.getText());
    queue.add("6");
    Assert.assertEquals("3456", queue.getText());
    queue.add("7");
    Assert.assertEquals("4567", queue.getText());
    queue.add("8");
    Assert.assertEquals("5678", queue.getText());
    queue.add("9");
    Assert.assertEquals("6789", queue.getText());
  }

  @Test
  public void testOverflowComplex() {
    CircularCharBuffer queue = new CircularCharBuffer(1, 10);
    String alphabet = "abcdefghijklmnopqrstuvwxyz";
    queue.add(alphabet);
    Assert.assertEquals(alphabet.substring(alphabet.length() - 10), queue.getText());
    queue.add(alphabet);
    Assert.assertEquals(alphabet.substring(alphabet.length() - 10), queue.getText());
    queue.add(alphabet.substring(0, 4));
    Assert.assertEquals("uvwxyzabcd", queue.getText());
    queue.poll();
    Assert.assertEquals("vwxyzabcd", queue.getText());
    queue.add("12");
    Assert.assertEquals("wxyzabcd12", queue.getText());
  }
}
