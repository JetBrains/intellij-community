import java.util.*;

class ListUnboxing {

  void m(List<Integer> children) {
    <caret>for (int i = 0; i < children.size(); i++) {
      int child = children.get(i);
      if (child == Integer.valueOf(10000)) {
        throw null;
      }
    }
  }
}