// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
class MultiDimensionalArray {
  static void main(args) {
    def x = new Closure[]{
      <error descr="Closures are not allowed in array initializer">{ println "hello world!" }</error>,
      <error descr="Closures are not allowed in array initializer">{ a -> println a }</error>,
      <error descr="Closures are not allowed in array initializer">{c, d -> a + b}</error>,
      <error descr="Closures are not allowed in array initializer">{(e) -> e}</error>,
      <error descr="Closures are not allowed in array initializer">{(f, g) -> f + g}</error>,
      <error descr="Closures are not allowed in array initializer">{(Integer h) -> h}</error>,
      <error descr="Closures are not allowed in array initializer">{(Integer j, Integer k) -> j + k}</error>
    }
  }
}