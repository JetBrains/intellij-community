package com.siyeh.igfixes.performance.trivial_string_concatenation;

class Parentheses2 {
  void m(String version) {
    final String s = " (" + (""<caret>) + "Groovy " + (version) + ")";
  }
}