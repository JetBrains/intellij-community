package pkg;

import java.lang.Override;
import java.lang.Runnable;

public class TestThrowException {
  Runnable r;
  public TestThrowException(int a) {
    if (a > 0) {
      throw new IllegalArgumentException("xxx");
    }
    r = new Runnable() {
      @Override
      public void run() {
        int a = 5;
      }
    };
  }
}
