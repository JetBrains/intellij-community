package com.siyeh.igfixes.style.replace_with_string;

class NoConstructorArgument {
  void m() {
    final StringBuilder buffer<caret> = new StringBuilder();
    buffer.append("appended");
    buffer.append("appended");

    String s = buffer.toString();
  }
}