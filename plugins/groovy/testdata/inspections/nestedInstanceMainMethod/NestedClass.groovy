// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

void main() {
}

class A {
  void main() {}

  static class B {
    void regularMethod() {}

    void <warning descr="Instance main method inside main class might not be executed">main</warning>() {}
  }

  static class C {
    void regularMethod() {}

    void <warning descr="Instance main method inside main class might not be executed">main</warning>(args) {}
  }

  static class D {
    void regularMethod() {}

    void <warning descr="Instance main method inside main class might not be executed">main</warning>(String[] args) {}
  }

  static class E {
    void regularMethod() {}

    def <warning descr="Instance main method inside main class might not be executed">main</warning>() {}
  }

  static class F {
    void regularMethod() {}

    def <warning descr="Instance main method inside main class might not be executed">main</warning>(args) {}
  }

  static class G {
    void regularMethod() {}

    def <warning descr="Instance main method inside main class might not be executed">main</warning>(String[] args) {}
  }
}