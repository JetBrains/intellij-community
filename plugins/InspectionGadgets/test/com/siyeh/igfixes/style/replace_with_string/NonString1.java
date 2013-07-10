package com.siyeh.igfixes.style.replace_with_string;

class NonString1 {

  String foo(CharSequence text) {
    return new <caret>StringBuilder(text).toString(); // no toString() because of NPEs
  }
}