package pkg;

import java.lang.Override;
import java.lang.Runnable;

public abstract class TestAbstractMethods {

  public abstract void foo();

  public int test(int a) {
    return a;
  }

  protected abstract void foo1();

  public void test2(String a) {
    System.out.println(a);
  }
}
