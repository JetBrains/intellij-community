// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
class B extends A {
  void usage() {
    String s = new A().<caret>fld
  }
}