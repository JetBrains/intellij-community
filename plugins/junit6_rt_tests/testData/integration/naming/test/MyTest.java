// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import org.junit.jupiter.api.Test;

class MyTest {
  @Test void foo(int[] i) {}
  @Test void foo(int i) {}
  @Test void foo(String[] i) {}
  @Test void foo(String i) {}
  @Test void foo(Foo[] i) {}
  @Test void foo(Foo i) {}

  static class Foo {}
}
