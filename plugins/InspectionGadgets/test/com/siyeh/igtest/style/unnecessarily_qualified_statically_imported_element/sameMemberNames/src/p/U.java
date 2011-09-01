package p;

import static p.EnumA.values;
import static p.A.a;

class U {
  void foo() {
    EnumA[] aValues = values();
    EnumB[] bValues = EnumB.values();
  }
  
  void bar() {
    a();
    B.a();
  }

}