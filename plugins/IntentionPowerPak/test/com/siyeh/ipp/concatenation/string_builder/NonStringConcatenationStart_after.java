package com.siyeh.ipp.concatenation.string_builder;

public class NonStringConcatenationStart {

  String foo() {
      //keep me
      return new StringBuilder().append(1 + 2).append("asdf").toString();
  }
}
