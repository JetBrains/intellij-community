// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
class Test {
  static StringBuilder create() {
    return new StringBuilder();
  }

  void test() {
    StringBuilder sb = Test.create().ap<caret>pend(/*comment*/"x")/*comment2*/.append(/*comment3*/"y")/*comment4*/.append(/*comment5*/"z");
  }
}