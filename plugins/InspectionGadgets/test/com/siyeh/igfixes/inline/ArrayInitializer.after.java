package com.siyeh.igfixes.inline;

class ArrayInitializer {

  void m(String[] ts) {
      ts = new String[]{"a", "b"};
  }
}