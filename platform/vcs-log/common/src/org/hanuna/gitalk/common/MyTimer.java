package org.hanuna.gitalk.common;

import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class MyTimer {
  private long timestamp = System.currentTimeMillis();
  private String message = "timer:";

  public MyTimer() {
  }

  public MyTimer(@NotNull String message) {
    this.message = message;
  }

  public void clear() {
    timestamp = System.currentTimeMillis();
  }

  public void clear(@NotNull String message) {
    this.message = message;
  }

  public long get() {
    return System.currentTimeMillis() - timestamp;
  }

  public void print() {
    long ms = get();
    System.out.println(message + ":" + ms);
  }
}
