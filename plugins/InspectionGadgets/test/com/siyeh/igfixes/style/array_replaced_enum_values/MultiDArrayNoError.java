// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
class MultiDArrayNoError {

  public static void foo() {
    testMethod(new TestEnum[][]{{Test<caret>Enum.ONE, TestEnum.THREE, TestEnum.TWO}});
  }

  private static void testMethod(TestEnum[][] values) {
  }

  public enum TestEnum {
    ONE, TWO, THREE;
  }
}