/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.util;

import junit.framework.TestCase;

/**
 * @author peter
 */
public class RecursionManagerTest extends TestCase {
  private final RecursionGuard myGuard = RecursionManager.createGuard("RecursionManagerTest");
  
  def prevent(Object key, boolean memoize = true, Closure c) {
    myGuard.doPreventingRecursion(key, memoize, c as Computable)
  }

  public void testPreventRecursion() {
    assert "foo-return" == prevent(["foo"]) {
      assert "bar-return" == prevent("bar") {
        assert null == prevent(["foo"]) { "foo-return" }
        return "bar-return"
      }
      return "foo-return"
    }
  }

  public void testMemoization() {
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

  public void testNoMemoizationAfterExit() {
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

  public void testMayCache() {
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

  public void testNoCachingForMemoizedValues() {
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

  public void testNoCachingForMemoizedValues2() {
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

  public void testNoMemoizationForNoReentrancy() {
    assert "foo-return" == prevent("foo") {
      assert null == prevent("foo") { "foo-return" }
      assert "bar-return" == prevent("bar") { "bar-return" }
      def stamp = myGuard.markStack()
      assert "bar-return2" == prevent("bar") { "bar-return2" }
      assert stamp.mayCacheNow()
      return "foo-return"
    }
  }

  public void testFullGraphPerformance() throws Exception {
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

  public void "test changing hash code doesn't crash RecursionManager"() {
    def key = ["b"]
    prevent(key) {
      key << "a"
    }
  }

  public void "test exception from hashCode on exiting"() {
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
