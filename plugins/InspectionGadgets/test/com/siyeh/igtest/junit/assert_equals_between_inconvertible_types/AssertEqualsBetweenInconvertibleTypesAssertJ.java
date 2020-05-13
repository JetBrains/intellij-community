package com.siyeh.igtest.junit.assert_equals_between_inconvertible_types;

import org.assertj.core.api.Assertions;

class MyTest {
  void myTest() {
    Assertions.assertThat("foo").<warning descr="'isEqualTo()' between objects of inconvertible types 'int' and 'String'">isEqualTo</warning>(2);
    Assertions.assertThat("foo").isEqualTo("bar");
    Assertions.assertThat("foo").describedAs("foo").<warning descr="'isEqualTo()' between objects of inconvertible types 'int' and 'String'">isEqualTo</warning>(2);
  }
}