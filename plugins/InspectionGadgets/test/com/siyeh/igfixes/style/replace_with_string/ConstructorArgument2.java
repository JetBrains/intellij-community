package com.siyeh.igfixes.style.replace_with_string;

class ConstructorArgument2 {
  void m() {
    final StringBuilder buffer<caret> = new StringBuilder(100);
    buffer.append("appended");

    String s = buffer.toString();
  }
}