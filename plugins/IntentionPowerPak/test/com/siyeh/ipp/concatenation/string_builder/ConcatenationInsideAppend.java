package com.siyeh.ipp.concatenation.string_builder;

public class ConcatenationInsideAppend {

  StringBuilder foo() {
    return new StringBuilder().append("asdf" <caret>//inside append
                                      + 1 //after second arg
    );//after end
  }
}