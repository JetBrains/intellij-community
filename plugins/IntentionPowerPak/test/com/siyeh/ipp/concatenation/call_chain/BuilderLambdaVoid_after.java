// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

class Test {
  void test(StringBuilder sb) {
    Runnable runnable = () -> {
        sb.append("foo");
        sb.append("bar");<caret>
    };
  }
}