// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestReporter;

import java.util.Iterator;
import java.util.stream.Stream;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class EnumSourceTest {
  @ParameterizedTest
  @EnumSource(value = Foo.class, names = <warning descr="Can't resolve 'enum' constant reference.">"invalid-value"</warning>, mode = EnumSource.Mode.INCLUDE)
  void invalid() {
  }

  @ParameterizedTest
  @EnumSource(value = Foo.class, names = <warning descr="Can't resolve 'enum' constant reference.">"invalid-value"</warning>)
  void invalidDefault() {
  }

  @ParameterizedTest
  @EnumSource(value = Foo.class, names = "regexp-value", mode = EnumSource.Mode.MATCH_ALL)
  void disable() {
  }

  @ParameterizedTest
  @EnumSource(value = Foo.class, names = {"BBB", "AAX"/*, "commented"*/})
  void array() {
  }

  @ParameterizedTest
  @EnumSource(value = Foo.class, names = "AAA", mode = EnumSource.Mode.INCLUDE)
  void withMode() {
  }

  @ParameterizedTest
  @EnumSource(value = Foo.class, names = {<warning descr="Can't resolve 'enum' constant reference.">""</warning>})
  void empty() {
  }

  @ParameterizedTest
  @EnumSource(value = Empty.class, names = {<warning descr="Can't resolve 'enum' constant reference.">"S"</warning>})
  void emptyEnum() {
  }

  private enum Foo {AAA, AAX, BBB}

  private enum Empty {}
}
class WithMetaAnnotations {
  @EnumSource(value = Foo.class, names = {""})
  @interface MetaEnumSource {}

  @ParameterizedTest
  @MetaEnumSource
  void <warning descr="Can't resolve 'enum' constant reference.">empty</warning>() { }

  private enum Foo {AAA}
}

class WithDefaultEnumSource {
  @ParameterizedTest
  @EnumSource
  void emptyES(Foo foo) { }

  private enum Foo {AAA}
}