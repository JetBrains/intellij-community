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
  
  def prevent(String key, boolean memoize = true, Closure c) {
    myGuard.doPreventingRecursion(key, memoize, c as Computable)
  }

  public void testPreventRecursion() {
    assert "foo-return" == prevent("foo") {
      assert "bar-return" == prevent("bar") {
        assert null == prevent("foo") { "foo-return" }
        return "bar-return"
      }
      return "foo-return"
    }
  }

  public void testMemoization() {
    def barVisited = false
    assert "foo-return" == prevent("foo") {
      assert "bar-return" == prevent("bar") {
        barVisited = true
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

}
