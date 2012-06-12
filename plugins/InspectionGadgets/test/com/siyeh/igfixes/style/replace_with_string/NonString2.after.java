package com.siyeh.igfixes.style.replace_with_string;

class NonString2 {

  String foo(Object o) {
    return o.toString();
  }
}