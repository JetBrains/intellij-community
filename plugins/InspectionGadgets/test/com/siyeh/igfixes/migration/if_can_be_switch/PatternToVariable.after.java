import org.jetbrains.annotations.Nullable;

class Test {
  void test(@Nullable Object o) {
      String r = switch (o) {
          case String s && s.length() > 3 -> s.substring(0, 3);
          case Integer integer -> "integer";
          case null, default -> "default";
      };
  }
}