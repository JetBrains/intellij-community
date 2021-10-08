import org.jetbrains.annotations.Nullable;

class Comment {
  String foo(@Nullable String s) {
    <caret>if("a".equals(s)) {
      System.out.println(1);
    } else if ("b".equals(s)) {
      System.out.println(2);
    } else if ("c".equals(s)) {
      System.out.println(3);
    } else {
      System.out.println(4);
    }
  }
}