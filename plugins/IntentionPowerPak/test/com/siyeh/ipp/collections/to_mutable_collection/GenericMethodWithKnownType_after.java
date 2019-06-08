import java.util.*;

class Test {

  <T> void test(T obj) {
      Set<T> x = new HashSet<>();
      x.add(obj);
      System.out.println(x);
  }

}