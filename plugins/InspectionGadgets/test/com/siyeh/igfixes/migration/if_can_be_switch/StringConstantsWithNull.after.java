import org.jetbrains.annotations.Nullable;

class Comment {
  String foo(@Nullable String s) {
      <caret>switch (s) {
          case "a" -> System.out.println(1);
          case "b" -> System.out.println(2);
          case "c" -> System.out.println(3);
          case null, default -> System.out.println(4);
      }
  }
}