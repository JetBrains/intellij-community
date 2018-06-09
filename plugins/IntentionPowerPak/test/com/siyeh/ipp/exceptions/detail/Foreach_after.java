package com.siyeh.ipp.exceptions.detail;

import java.util.List;

class Foreach {

  void foo(List<String> list) {
      try {
          for (String s: list) {
              throw new IllegalArgumentException();
          }
      } catch (IllegalArgumentException e) {
      }
  }
}