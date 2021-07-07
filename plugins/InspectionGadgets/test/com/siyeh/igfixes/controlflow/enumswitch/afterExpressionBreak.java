// "Create missing branches: 'B', and 'C'" "true"
package com.siyeh.ipp.enumswitch;

class BeforeDefault {
  enum X {A, B, C}
  
  String test(X x) {
    return switch (x) {
      case A -> "foo";
        case B -> null;
        case C -> null;
        default -> "bar";
    };
  }
}