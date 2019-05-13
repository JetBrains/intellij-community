package pkg;

import java.lang.Override;
import java.lang.Runnable;

public class TestSynchronizedMapping {
  public int test(int a) {
    synchronized (this) {
      a++;
    }
    return a++;
  }

  public void test2(String a) {
    System.out.println(a);
  }
}
