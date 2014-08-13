package com.siyeh.igtest.bugs.mismatched_string_builder_query_update;

public class MismatchedStringBuilderQueryUpdate {

  void foo() {
    final StringBuilder b = new StringBuilder();
    b.append("");
    System.out.println("" + b + "");

    final StringBuilder <warning descr="Contents of StringBuilder 'c' are updated, but never queried">c</warning> = new StringBuilder();
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
}
interface List<T> {
  default void forEach(Consumer<? super T> action) {
  }
}
interface Consumer<T> {
  void accept(T t);
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
