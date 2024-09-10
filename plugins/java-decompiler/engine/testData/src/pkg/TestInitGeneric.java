package pkg;

import java.util.List;

public class TestInitGeneric<T> {


  public static void main(String[] args) {

  }

  public T test() {
    return null;
  }

  class A<T> {
    public T test() {
      return null;
    }
  }

  class B<L extends T> {
    public L test() {
      return null;
    }

    public <AA extends CharSequence> AA test2() {
      return null;
    }
  }

  static class C<L extends CharSequence, K> {
    public K test(List<? super L> list) {
      return null;
    }

    public L test2(List<? extends L> list) {
      L l = list.get(0);
      System.out.println(l);
      return l;
    }


  }
}