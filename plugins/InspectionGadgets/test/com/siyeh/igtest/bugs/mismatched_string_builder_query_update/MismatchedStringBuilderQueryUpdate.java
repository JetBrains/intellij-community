package com.siyeh.igtest.bugs.mismatched_string_builder_query_update;

import java.util.*;
import java.util.function.*;

public class MismatchedStringBuilderQueryUpdate {

  public void testAssert() {
    StringBuilder finished = new StringBuilder();

    finished.append("3 ");
    Assert.assertEquals("expected", finished.toString());
  }

  void foo() {
    final StringBuilder b = new StringBuilder();
    b.append("");
    System.out.println("" + b + "");

    final StringBuilder <warning descr="Contents of 'StringBuilder c' are updated, but never queried">c</warning> = new StringBuilder();
    c.append(' ');
  }

  private static CharSequence getSomething()
  {
    StringBuilder sb = new StringBuilder();
    sb.append("abc");
    return sb.reverse();
  }

  void indexedList(List<String> list) {
    StringBuilder stringBuilder = new StringBuilder(); // <--- false warning here
    list.forEach(stringBuilder::append);
    System.out.println(stringBuilder.toString());
  }

  public static void main(String[] args) {
    Map<String, StringBuilder> map = Collections.singletonMap("buf", new StringBuilder());
    test(map);
    System.out.println(map.get("buf"));
  }

  private static void test(Map<String, StringBuilder> map) {
    // should not be marked as builder is obtained from outside IDEA-162479
    StringBuilder builder = map.get("buf");
    builder.append("xyz");
  }

  static String testUselessQuery(java.util.List<String> list) {
    StringBuilder <warning descr="Contents of 'StringBuilder sb' are updated, but never queried">sb</warning> = new StringBuilder();
    for (String s : list) {
      if (s != null) {
        if (sb.length() > 0) {
          sb.append(',');
        } else {
          sb.append("start");
        }
        sb.append(s.length());
      }
    }
    return null;
  }

  static String testUselessQueryTernary(java.util.List<String> list) {
    StringBuilder <warning descr="Contents of 'StringBuilder sb' are updated, but never queried">sb</warning> = new StringBuilder();
    for (String s : list) {
      if (s != null) {
        if(sb.length() > 0) {
          assert sb.length() > 5;
        }
        sb.append(sb.length() > 0 ? ',' : "start");
        sb.append(s.length());
      }
    }
    return null;
  }

  static String testQueryWithSideEffect(java.util.List<String> list) {
    StringBuilder sb = new StringBuilder();
    for (String s : list) {
      if (s != null) {
        if (sb.length() > 0) {
          sb.append(',');
          System.out.println("Hello");
        }
        sb.append(s.length());
      }
    }
    return null;
  }

  public static void methodRefQuery(String name) {
    final StringBuilder sb = new StringBuilder();
    sb.append("Hello, ").append(name != null ? name : "world");
    foo(sb::toString);

    final StringBuilder sb2 = new StringBuilder();
    sb2.append("Hello, ").append(name != null ? name : "world");
    foo2(sb2::append); // StringBuilder can leak, because it's returned from function

    final StringBuilder <warning descr="Contents of 'StringBuilder sb3' are updated, but never queried">sb3</warning> = new StringBuilder();
    sb3.append("Hello, ").append(name != null ? name : "world");
    foo3(sb3::append); // StringBuilder cannot leak
  }

  static void foo(Supplier<String> s) {
    System.out.println(s.get());
  }

  static void foo2(Function<String, StringBuilder> fn) {
    System.out.println(fn.apply("foo"));
  }

  static void foo3(Consumer<String> fn) {
    fn.accept("foo");
  }
}
class EnumConstant {
  private static final StringBuilder sb = new StringBuilder();
  static {
    sb.append("");
  }

  enum SomeEnum {
    ITEM(sb); // passed as argument

    SomeEnum(StringBuilder sb) {}
  }
}
class Assert {
  static Object assertEquals(Object a, Object b) {
    return a;
  }
}
class Main {

  static String foo() {
    StringBuilder sb = new StringBuilder();
    sb.append("txet");
    return "some " + sb.reverse();
  }

  public static void main(String[] args) {
    System.out.println(foo());
  }
}
class Sample {
  private final int NUMBER = 1;
  CharSequence sequence = "test";

  public void foo(int type) {
    final String tokenValue = switch (type) {
      case NUMBER -> {
        final StringBuilder  value = new StringBuilder();
        iterateWhile(Character::isDigit, value::append);
        yield value.toString();
      }

      default -> throw new IllegalStateException("Unexpected value: " + type);
    };

  }

  private void iterateWhile(Predicate<Character> condition, Consumer<Character> action) {
    int currentIndex = 0;
    while (currentIndex < sequence.length() && condition.test(sequence.charAt(currentIndex))) {
      action.accept(sequence.charAt(currentIndex));
      currentIndex++;
    }
  }
}