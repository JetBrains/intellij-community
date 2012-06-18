package com.siyeh.ipp.concatenation.string_builder;

public class ConcatenationInsideAppend {

  StringBuilder foo() {
    return new StringBuilder().append("asdf").append(1);
  }
}