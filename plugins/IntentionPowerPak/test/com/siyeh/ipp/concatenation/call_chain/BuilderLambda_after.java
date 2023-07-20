// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import java.util.function.Supplier;

class Test {
  void test(StringBuilder sb) {
    Supplier<StringBuilder> supplier = () -> {
        sb.append("foo");
        sb.append("bar");
        return <caret>sb;
    };
  }
}