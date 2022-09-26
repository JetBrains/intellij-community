import org.jetbrains.annotations.NotNull;

class Test {
  void test(@NotNull Object o, int variable) {
        <caret>if (o instanceof String s && !s.isEmpty()) {
      System.out.println();
    } else if (o instanceof Integer x && x > 0 && x < 10) {
      System.out.println();
    } else if (variable > 0 && o instanceof Float) {
      System.out.println();
    }
  }
}