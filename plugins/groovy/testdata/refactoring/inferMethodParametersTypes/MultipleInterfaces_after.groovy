def <T extends B & C> void foo(T a) {
  a.doB()
  a.doC()
}

interface B {
  void doB();
}

interface C {
  void doC();
}

class Cl implements B, C {
  void doB() {}
  void doC() {}
}

class Bl implements B, C {
  void doB() {}
  void doC() {}
}

void m(Bl li, Cl ls) {
  foo(li);
  foo(ls);
}