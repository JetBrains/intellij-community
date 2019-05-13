// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
class Barr {
  private final String s;

  enum Foo {}

  Barr(String s, Foo... foos) {
    this.s = s;
    new A().foo<caret>Moo = foos;
  }
}