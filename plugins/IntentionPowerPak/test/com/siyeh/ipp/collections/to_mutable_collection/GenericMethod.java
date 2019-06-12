import java.util.*;

class Test {

  static <T extends Comparable<T>> void use(Collection<? extends T> c) { }

  void test() {
    use(Collections.singleton<caret>(null));
  }
}