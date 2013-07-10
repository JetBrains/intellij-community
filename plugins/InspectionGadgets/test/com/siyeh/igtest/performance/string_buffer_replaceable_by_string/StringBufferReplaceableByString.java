package com.siyeh.igtest.performance.string_buffer_replaceable_by_string;

public class StringBufferReplaceableByString {

  StringBuilder foo() {
    StringBuilder builder = new StringBuilder("asdfasdfasdf");
    StringBuffer buffer = new StringBuffer("test");
    StringBuilder result = new StringBuilder("return");
    return result;
  }

  public void foo1()
  {
    final StringBuffer buffer = new StringBuffer().append('a');
    System.out.println(buffer.toString());
  }

  public void foo2()
  {
    final StringBuffer buffer = new StringBuffer("foo").append("bar");
    System.out.println(buffer.toString());
  }

  public void bar(int i) {
    System.out.println(new StringBuilder("asdf").append(i).toString());
  }

  public void exceptions(String pcdata, int i) {
    StringBuilder b = new StringBuilder();
    String s = new StringBuilder().append(pcdata, 0, i).toString();
  }

  public void multiStatement() {
    StringBuilder b = new StringBuilder();
    b.append("eh");
    b.append(" yeah").append("thus...");
    System.out.println(b.toString());
  }

  public void assignment(int p) {
    StringBuilder b = new StringBuilder();
    b.append(p);
    p++;
    System.out.println(b.toString());
    StringBuilder c = new StringBuilder();
    c.append(p);
    p = 2;
    System.out.println(c.toString());

    StringBuilder d = new StringBuilder();
    d.append(p);
    System.out.println(d.toString());
  }

  void clean() {
    StringBuilder a = new StringBuilder(); // 'StringBuilder a' can be replaced with String
    StringBuilder b = new StringBuilder(); // 'StringBuilder b' can be replaced with String

    (Math.random() < 0.5 ? a : b).append("BLA");
    System.out.println(a + "/" + b);
  }

  String incomplete(char[] cs) {
    StringBuilder a = new StringBuilder();
    a.append(cs, 1);
    System.out.println(a.toString());
    StringBuilder b = new StringBuilder();
    b.append()
    return b.toString();
  }

  public static String formatOffset(long offsetMs) {
    final long minutes = offsetMs / 1000 / 60;
    final long hours = minutes / 60;
    final long min = Math.abs(minutes - hours * 60);
    StringBuilder data = new StringBuilder();
    if (hours > 0) data.append('+');
    if (hours < 0) data.append('-');
    data.append(String.format("%02d:%02d", Math.abs(hours), min));
    return data.toString();
  }
}
