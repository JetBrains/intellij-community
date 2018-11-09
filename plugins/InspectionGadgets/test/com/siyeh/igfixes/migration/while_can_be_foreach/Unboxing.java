import java.util.*;

class Unboxing {

  void m(List<Integer> sheep) {
    Iterator<Integer> iterator = sheep.iterator();
    while<caret> (iterator.hasNext()) {
      int i = iterator.next();
      if (i == Integer.valueOf(10000)) {
        throw null;
      }
    }
  }
}