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
}
