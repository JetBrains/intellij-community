package com.siyeh.igfixes.style.replace_with_string;

class Array {
  String array(char[] cs) {
    return String.valueOf(cs) + String.valueOf(cs, 0, 10);
  }
}
