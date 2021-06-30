import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class NullUnsafe {

  void annotationNullable(@Nullable String s) {
    if ("a".equals((s))) {
      System.out.println(1);
    } else if ("b".equals(s)) {
      System.out.println(2);
    } else if ("c".equals(s)) {
      System.out.println(3);
    } else {
      System.out.println(4);
    }
  }

  void inferredNullable(int x) {
    String s = x > 10 ? "Nullable" : null;
    if ("a".equals((s))) {
      System.out.println(1);
    } else if ("b".equals(s)) {
      System.out.println(2);
    } else if ("c".equals(s)) {
      System.out.println(3);
    } else {
      System.out.println(4);
    }
  }

  void unknown(String s) {
    <warning descr="'if' statement can be replaced with 'switch' statement">if</warning> ("a".equals((s))) {
      System.out.println(1);
    } else if ("b".equals(s)) {
      System.out.println(2);
    } else if ("c".equals(s)) {
      System.out.println(3);
    } else {
      System.out.println(4);
    }
  }

  void unknownPattern(Object o) {
    <warning descr="'if' statement can be replaced with 'switch' statement">if</warning> (o instanceof String) {
      System.out.println(1);
    } else if (o instanceof Integer) {
      System.out.println(2);
    }
  }
}