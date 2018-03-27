package com.siyeh.igtest.style.equals_called_on_enum_constant;

public class EqualsCalled {

    enum E {
        A,B,C
    }

    void one() {
        E.A.<warning descr="'equals()' called on Enum value">equals</warning>(E.C);
        E.B.<warning descr="'equals()' called on Enum value">equals</warning>(new Object());
        E.C.equals<error descr="'equals(java.lang.Object)' in 'java.lang.Enum' cannot be applied to '()'">()</error>;
        final Object A = new Object();
        A.equals(1);
    }

    void objectsEquals(E a, E b) {
      if(java.util.Objects.<warning descr="'equals()' called on Enum value">equals</warning>(a, b)) {
        System.out.println("equals");
      }
    }
}
class Main {
  enum Suit {
    SPADES, HEARTS, DIAMONDS, CLUBS
  }

  private boolean equalsType(Suit suit, String type) {
    return suit.equals(type);
  }
}
