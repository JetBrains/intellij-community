package com.siyeh.ipp.concatenation.string_builder;

public class ConcatenationInsideAppend {

  StringBuilder foo() {
      //inside append
      //after second arg
      return new StringBuilder().append("asdf").append(1);//after end
  }
}