import org.jetbrains.annotations.*;

class Test {
  void test(@Nullable Integer i) {
    switch ((<caret>i)) {
      case 1 -> System.out.println();
      case default -> System.out.println();
    }
  }
}