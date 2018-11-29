// "Create missing branches: 'B', and 'E'" "true"
package com.siyeh.ipp.enumswitch;

class BeforeDefault {
  enum X {A, B, C, D, E, F}
  
  String test(X x) {
    return switch (x) {
      case A -> "foo";
        case B -> {
        }
        case C, D -> "bar";
        case E -> {
        }
        case F -> "baz";
      default -> throw new AssertionError();
    };
  }
}