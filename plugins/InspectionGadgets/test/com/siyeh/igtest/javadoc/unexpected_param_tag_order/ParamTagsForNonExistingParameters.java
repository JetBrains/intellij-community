// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
class ParamTagsForNonExistingParameters {

  /**
   * @param x
   * @param y
   * @param z
   * @param <A>
   * @param <B>
   * @param <C>
   */
  <X, Y, Z> Z foo1(X a, Y b, Z c) {return null;}

  /**
   * @param x
   */
  <X, Y, Z> Z foo2(X a, Y b, Z c) {return null;}
}

/**
 * @param <A>
 * @param <B>
 * @param <C>
 */
class Foo1<X, Y, Z> {}

/**
 * @param <A>
 * @param <B>
 * @param <C>
 */
interface Foo2<X, Y, Z> {}