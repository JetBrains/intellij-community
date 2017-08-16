package com.intellij.util.containers;

import junit.framework.Assert;

/**
 * @author Sergey Simonchik
 */
public class ByteArrayQueueTest {

  @org.junit.Test
  public void testSingleAdd() {
    ByteArrayQueue queue = new ByteArrayQueue(0);
    byte value = 1;
    queue.add(value);
    Assert.assertEquals(1, queue.size());
    Assert.assertEquals(value, queue.poll());
    Assert.assertEquals(-1, queue.poll());
  }

  @org.junit.Test
  public void testResize1() {
    ByteArrayQueue queue = new ByteArrayQueue(4);
    byte[] buf = new byte[] {1, 2, 3, 4, 5};
    queue.addAll(buf);
    Assert.assertEquals(queue.size(), buf.length);
    for (byte b : buf) {
      Assert.assertEquals(b, queue.poll());
    }
    Assert.assertEquals(-1, queue.poll());
  }

  @org.junit.Test
  public void testResize2() {
    ByteArrayQueue queue = new ByteArrayQueue(4);
    byte[] buf = new byte[] {1, 2, 3, 4, 5, 6, 7, 8};
    queue.addAll(buf);
    Assert.assertEquals(queue.size(), buf.length);
    Assert.assertEquals(buf[0], queue.poll());
    Assert.assertEquals(buf[1], queue.poll());
    byte v = 9;
    queue.add(v);
    queue.add(v);

    queue.addAll(buf); // array resize with myHead > myTail
    Assert.assertEquals(queue.size(), buf.length * 2);
    for (int i = 2; i < buf.length; i++) {
      Assert.assertEquals(buf[i], queue.poll());
    }
    Assert.assertEquals(v, queue.poll());
    Assert.assertEquals(v, queue.poll());
    for (byte b : buf) {
      Assert.assertEquals(b, queue.poll());
    }
    Assert.assertEquals(-1, queue.poll());
  }

}
