// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

static void main() {
}

class A {
  static void main() {}

  static class B {
    static void regularMethod() {}

    static void main() {}
  }

  static class C {
    void regularMethod() {}

    static void main(args) {}
  }

  static class D {
    static void regularMethod() {}

    static void main(String[] args) {}
  }

  static class E {
    static void regularMethod() {}

    static def main() {}
  }

  static class F {
    static void regularMethod() {}

    static def main(args) {}
  }

  static class G {
    static void regularMethod() {}

    static def main(String[] args) {}
  }
}