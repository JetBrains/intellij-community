// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
class A {
  static void <warning descr="Another main method defined in the class 'A'">main</warning>() {

  }

  void <warning descr="Another main method defined in the class 'A'">main</warning>(args) {
  }

  void method() {
  }
}