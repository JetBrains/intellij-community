// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
class T {
  void foo(char i) {
    sw<caret>itch (i) {
    case '0':
    System.out.println(i);
    }
  }
}