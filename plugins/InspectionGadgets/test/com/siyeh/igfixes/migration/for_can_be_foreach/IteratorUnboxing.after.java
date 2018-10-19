import java.util.*;

class IteratorUnboxing {

  void m(List<Integer> children) {
      for (int child : children) {
          if (child == Integer.valueOf(10000)) {
              throw null;
          }
      }
  }
}