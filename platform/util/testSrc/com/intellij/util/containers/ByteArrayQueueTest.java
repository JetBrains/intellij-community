/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.util.containers;

import static junit.framework.TestCase.assertEquals;

/**
 * @author Sergey Simonchik
 */
public class ByteArrayQueueTest {
  @org.junit.Test
  public void testSingleAdd() {
    ByteArrayQueue queue = new ByteArrayQueue(0);
    byte value = 1;
    queue.add(value);
    assertEquals(1, queue.size());
    assertEquals(value, queue.poll());
    assertEquals(-1, queue.poll());
  }

  @org.junit.Test
  public void testResize1() {
    ByteArrayQueue queue = new ByteArrayQueue(4);
    byte[] buf = new byte[] {1, 2, 3, 4, 5};
    queue.addAll(buf);
    assertEquals(queue.size(), buf.length);
    for (byte b : buf) {
      assertEquals(b, queue.poll());
    }
    assertEquals(-1, queue.poll());
  }

  @org.junit.Test
  public void testResize2() {
    ByteArrayQueue queue = new ByteArrayQueue(4);
    byte[] buf = new byte[] {1, 2, 3, 4, 5, 6, 7, 8};
    queue.addAll(buf);
    assertEquals(queue.size(), buf.length);
    assertEquals(buf[0], queue.poll());
    assertEquals(buf[1], queue.poll());
    byte v = 9;
    queue.add(v);
    queue.add(v);

    queue.addAll(buf); // array resize with myHead > myTail
    assertEquals(queue.size(), buf.length * 2);
    for (int i = 2; i < buf.length; i++) {
      assertEquals(buf[i], queue.poll());
    }
    assertEquals(v, queue.poll());
    assertEquals(v, queue.poll());
    for (byte b : buf) {
      assertEquals(b, queue.poll());
    }
    assertEquals(-1, queue.poll());
  }
}
