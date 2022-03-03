// "Create missing branches: 'A', 'B', 'C', 'D', 'E', and 'F'" "true"
package com.siyeh.ipp.enumswitch;

class BeforeDefault {
  enum X {A, B, C, D, E, F}

  String test(X x) {
    return switch (x<caret>) {
    };
  }
}