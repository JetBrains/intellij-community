import org.jetbrains.annotations.*;

class Test {
  void test(@Nullable Integer i) {
    switch ((i)) {
      case 1:
        break;
        case null:
            break;<caret>
        case Integer ii when true:
        System.out.println();
        break;
    }
  }
}