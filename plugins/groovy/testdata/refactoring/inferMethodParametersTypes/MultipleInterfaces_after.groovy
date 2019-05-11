def <T0 extends B & C> void foo(T0 a) {
  a.doB()
  a.doC()
}

// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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