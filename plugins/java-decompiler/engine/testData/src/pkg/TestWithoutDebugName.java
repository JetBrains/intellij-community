package org.example;

import java.util.LinkedList;
import java.util.Queue;

public class TestWithoutDebugName {

  private Queue<String> queue = new LinkedList<>();

  public Queue<String> getQueue() {
    return queue;
  }

  public void setQueue(Queue<String> queue) {
    this.queue = queue;
  }
}