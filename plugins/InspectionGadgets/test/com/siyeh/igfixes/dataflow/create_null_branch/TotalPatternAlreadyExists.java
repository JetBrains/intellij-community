import org.jetbrains.annotations.*;

class Test {
  void test(@Nullable Integer i) {
    switch ((<caret>i)) {
      case 1:
        break;
      case (Integer ii && true):
        System.out.println();
        break;
    }
  }
}