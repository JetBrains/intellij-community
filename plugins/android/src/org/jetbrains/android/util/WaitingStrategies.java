package org.jetbrains.android.util;

import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class WaitingStrategies {
  public static abstract class Strategy {
    private Strategy() {
    }
  }

  public static class DoNotWait extends Strategy {
    private static final DoNotWait INSTANCE = new DoNotWait();

    private DoNotWait() {
    }

    @NotNull
    public static DoNotWait getInstance() {
      return INSTANCE;
    }
  }

  public static class WaitForTime extends Strategy {
    private final int myTimeMs;

    private WaitForTime(int timeMs) {
      assert timeMs > 0;
      myTimeMs = timeMs;
    }

    @NotNull
    public static WaitForTime getInstance(int timeMs) {
      return new WaitForTime(timeMs);
    }

    public int getTimeMs() {
      return myTimeMs;
    }
  }

  public static class WaitForever extends Strategy {
    private static final WaitForever INSTANCE = new WaitForever();

    private WaitForever() {
    }

    @NotNull
    public static WaitForever getInstance() {
      return INSTANCE;
    }
  }
}
