// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util

import groovy.transform.Immutable
import junit.framework.TestCase

/**
 * @author peter
 */
class RecursionManagerTest extends TestCase {
  private final RecursionGuard myGuard = RecursionManager.createGuard("RecursionManagerTest")
  
  def prevent(Object key, boolean memoize = true, Closure c) {
    myGuard.doPreventingRecursion(key, memoize, c as Computable)
  }

  void testPreventRecursion() {
    assert "foo-return" == prevent(["foo"]) {
      assert "bar-return" == prevent("bar") {
        assert null == prevent(["foo"]) { "foo-return" }
        return "bar-return"
      }
      return "foo-return"
    }
  }

  void testMemoization() {
    assert "foo-return" == prevent("foo") {
      assert "bar-return" == prevent("bar") {
        assert null == prevent("foo") { "foo-return" }
        return "bar-return"
      }
      assert "bar-return" == prevent("bar") {
        fail()
      }
      return "foo-return"
    }
  }

  void testNoMemoizationAfterExit() {
    assert "foo-return" == prevent("foo") {
      assert "bar-return" == prevent("bar") {
        assert null == prevent("foo") { "foo-return" }
        return "bar-return"
      }
      return "foo-return"
    }
    assert "bar-return2" == prevent("bar") {
      return "bar-return2"
    }
  }

  void testMayCache() {
    def doo1 = myGuard.markStack()
    assert "doo-return" == prevent("doo") {
      def foo1 = myGuard.markStack()
      assert "foo-return" == prevent("foo") {
        def bar1 = myGuard.markStack()
        assert "bar-return" == prevent("bar") {
          def foo2 = myGuard.markStack()
          assert null == prevent("foo") { "foo-return" }
          assert !foo2.mayCacheNow()
          return "bar-return"
        }
        assert !bar1.mayCacheNow()
        
        def goo1 = myGuard.markStack()
        assert "goo-return" == prevent("goo") {
          return "goo-return"
        }
        assert goo1.mayCacheNow()
        assert !bar1.mayCacheNow()

        return "foo-return"
      }
      assert foo1.mayCacheNow()
      return "doo-return"
    }
    assert doo1.mayCacheNow()
  }

  void testNoCachingForMemoizedValues() {
    assert "foo-return" == prevent("foo") {
      assert "bar-return" == prevent("bar") {
        assert null == prevent("foo") { "foo-return" }
        return "bar-return"
      }
      def stamp = myGuard.markStack()
      assert "bar-return" == prevent("bar") {
        fail()
      }
      assert !stamp.mayCacheNow()
      return "foo-return"
    }
  }

  void testNoCachingForMemoizedValues2() {
    assert "1-return" == prevent("1") {
      assert "2-return" == prevent("2") {
        assert "3-return" == prevent("3") {
          assert null == prevent("2") { "2-return" }
          assert null == prevent("1") { "1-return" }
          return "3-return"
        }
        return "2-return"
      }
      def stamp = myGuard.markStack()
      assert "2-return" == prevent("2") { fail() }
      assert !stamp.mayCacheNow()

      stamp = myGuard.markStack()
      assert "3-return" == prevent("3") { fail() }
      assert !stamp.mayCacheNow()

      return "1-return"
    }
  }

  void testNoMemoizationForNoReentrancy() {
    assert "foo-return" == prevent("foo") {
      assert null == prevent("foo") { "foo-return" }
      assert "bar-return" == prevent("bar") { "bar-return" }
      def stamp = myGuard.markStack()
      assert "bar-return2" == prevent("bar") { "bar-return2" }
      assert stamp.mayCacheNow()
      return "foo-return"
    }
  }

  void testFullGraphPerformance() throws Exception {
    long start = System.currentTimeMillis()
    int count = 20
    Closure cl
    cl = {
      for (i in 1..count) {
        prevent("foo" + i, cl)
      }
      return "zoo"
    }

    assert "zoo" == cl()

    assert System.currentTimeMillis() - start < 10000
  }

  void "test changing hash code doesn't crash RecursionManager"() {
    def key = ["b"]
    prevent(key) {
      key << "a"
    }
  }

  void "test key equals that invokes RecursionManager"() {
    prevent(new RecursiveKey('a')) {
      prevent(new RecursiveKey('b')) {
        prevent(new RecursiveKey('a')) {
          throw new AssertionError("shouldn't be called")
        }
      }
    }
  }

  @Immutable
  private static class RecursiveKey {
    final String id

    @Override
    int hashCode() {
      return id.hashCode()
    }

    @Override
    boolean equals(Object obj) {
      RecursionManager.doPreventingRecursion("abc", false) { true }
      return obj instanceof RecursiveKey && obj.id == id
    }
  }

  void "test exception from hashCode on exiting"() {
    def key1 = new ThrowingKey()
    def key2 = new ThrowingKey()
    def key3 = new ThrowingKey()
    prevent(key1) {
      prevent(key2) {
        prevent(key3) {
          key1.fail = key2.fail = key3.fail = true
        }
      }
    }
  }

  private static class ThrowingKey {
    boolean fail = false

    @Override
    int hashCode() {
      if (fail) {
        throw new RuntimeException()
      }
      return 0
    }

    @Override
    boolean equals(Object obj) {
      if (fail) {
        throw new RuntimeException()
      }
      return super.equals(obj)
    }
  }

}
