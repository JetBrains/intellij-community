// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.igfixes.abstraction.type_may_be_weakened;

import java.util.ArrayList;
import java.util.List;

class B<T> {
  void foo() {}
}


class A extends B<String> { }


class Main {
  public static void main(String[] args) {
    B<String> <caret>a = new A();
    a.foo();
  }
}