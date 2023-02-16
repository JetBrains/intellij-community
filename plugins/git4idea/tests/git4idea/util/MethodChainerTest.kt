// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.util

import org.junit.Before
import org.junit.Test
import kotlin.test.assertIs

abstract class MethodChainerTest<T> {
  abstract var myType: T

  companion object {
    var runCountForMethod1 = 0
    var runCountForMethod2 = 0
  }

  protected fun myMethod1(myType: T): T {
    runCountForMethod1++
    return myType
  }

  protected fun myMethod2(myType: T): T {
    runCountForMethod2++
    return myType
  }

  @Before
  fun reset() {
    runCountForMethod1 = 0
    runCountForMethod2 = 0
  }

  /**
   * Tests if <code>MethodChainer.wrap(T type)</code> returns an object wrapped in a MethodChainer.
   * This does not guarantee the internal object is still of type T.
   */
  @Test
  fun testWrapReturnsAMethodChainerOfTypeT() {
    assertIs<MethodChainer<T>>(MethodChainer.wrap(myType))
  }

  @Test
  fun testRun() {
    MethodChainer.wrap(myType).run(::myMethod1)
    assert(runCountForMethod1 == 1)
  }

  @Test
  fun testRunIfForTrue() {
    MethodChainer.wrap(myType).runIf(true, ::myMethod1)
    assert(runCountForMethod1 == 1)
  }

  @Test
  fun testRunIfForFalse() {
    MethodChainer.wrap(myType).runIf(false, ::myMethod1)
    assert(runCountForMethod1 == 0)
  }

  @Test
  fun testRunIfElseForTrue() {
    MethodChainer.wrap(myType).runIfElse(true, ::myMethod1, ::myMethod2)
    assert(runCountForMethod1 == 1)
    assert(runCountForMethod2 == 0)
  }

  @Test
  fun testRunIfElseForFalse() {
    MethodChainer.wrap(myType).runIfElse(false, ::myMethod1, ::myMethod2)
    assert(runCountForMethod1 == 0)
    assert(runCountForMethod2 == 1)
  }

  @Test
  fun testUnwrap() {
    assert(MethodChainer.wrap(myType).unwrap() == myType)
  }
}