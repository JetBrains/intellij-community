// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
class Test {
  static StringBuilder create() {
    return new StringBuilder();
  }

  void test() {
      StringBuilder sb = Test.create();
      sb.append(/*comment*/"x");
      sb.append(/*comment3*/"y");
      sb.append(/*comment5*/"z");/*comment2*//*comment4*/
  }
}