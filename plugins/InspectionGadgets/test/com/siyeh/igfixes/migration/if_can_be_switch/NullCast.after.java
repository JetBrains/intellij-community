import org.jetbrains.annotations.Nullable;

class Test {
  int foo(@Nullable Integer x) {
      <caret>switch (x) {
          case null -> {
              return 123;
          }
          default -> {
          }
      }
    throw new IllegalStateException("Unexpected value: " + x);
  }
}