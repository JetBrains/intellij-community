package com.siyeh.igtest.bugs.mismatched_string_builder_query_update;

public class MismatchedStringBuilderQueryUpdate {

  void foo() {
    final StringBuilder b = new StringBuilder();
    b.append("");
    System.out.println("" + b + "");

    final StringBuilder c = new StringBuilder();
    c.append(' ');
  }

  private static CharSequence getSomething()
  {
    StringBuilder sb = new StringBuilder();
    sb.append("abc");
    return sb.reverse();
  }

  void indexedList(java.util.List<String> list) {
    StringBuilder stringBuilder = new StringBuilder(); // <--- false warning here
    list.forEach(stringBuilder::append);
    System.out.println(stringBuilder.toString());
  }
}
