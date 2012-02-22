package com.siyeh.igtest.performance.string_concatenation_inside_string_buffer_append;

public class StringConcatenationInsideStringBufferAppend {

  private String s;

  void foo(StringBuffer buffer) {
    buffer.append("asdf" + s + "asdf");
    buffer.append("asdf" + s);
    buffer.append("asdf" + "asdf");
  }

  void bar(StringBuilder builder) {
    builder.append("asdf" + s + "asdf");
    builder.append("asdf" + s);
    builder.append("asdf" + "asdf");
  }

  /*
  // java.lang.Appendable not in mock jdk
  void appendable(Appendable appendable) throws IOException {
    appendable.append("asdf" + s);
    appendable.append((s + "asdf"));
  }
  */
}
