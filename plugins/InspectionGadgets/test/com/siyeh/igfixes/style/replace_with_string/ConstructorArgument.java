package com.siyeh.igfixes.style.replace_with_string;

class ConstructorArgument {
  void m() {
    final StringBuilder buffer<caret> = new StringBuilder("init-");
    buffer.append("appended");

    String s = buffer.toString();
  }
}