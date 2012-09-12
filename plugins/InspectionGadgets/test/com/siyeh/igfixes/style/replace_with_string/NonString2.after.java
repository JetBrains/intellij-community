package com.siyeh.igfixes.style.replace_with_string;

class NonString2 {

  String foo(char[] o) {
    return String.valueOf(o);
  }
}