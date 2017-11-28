/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.psi.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.TimeoutUtil

import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function

/**
 * @author peter
 */
class CachedValuesTest extends LightPlatformTestCase {
  def holder = new UserDataHolderBase()

  void "test recreate cached value if outdated to avoid capturing invalid stuff"() {
    SimpleModificationTracker dependency = new SimpleModificationTracker()
    def log = []
    Function<String, String> getCached = { arg -> CachedValuesManager.getManager(project).getCachedValue(holder, {
      log << "capturing " + arg
      return CachedValueProvider.Result.create('result', dependency)
    }) }

    assert 'result' == getCached.apply('foo')

    dependency.incModificationCount()
    assert 'result' == getCached.apply('bar')
    assert log == ['capturing foo', 'capturing bar']
  }

  void "test calculate value at most once per thread"() {
    AtomicInteger calcCount = new AtomicInteger()
    SimpleModificationTracker dependency = new SimpleModificationTracker()
    Closure<String> getCached = { CachedValuesManager.getManager(project).getCachedValue(holder, {
      calcCount.incrementAndGet()
      TimeoutUtil.sleep(10)
      return CachedValueProvider.Result.create('result', dependency)
    }) }
    assert 'result' == getCached()
    assert calcCount.getAndSet(0) == 1

    for (int r=0; r<1000; r++) {
//      System.out.println("r = " + r)

      calcCount.set(0)
      dependency.incModificationCount()

      List<Future> jobs = (0..<4).collect {
        ApplicationManager.application.executeOnPooledThread {
          for (i in 0..10) {
            assert 'result' == getCached()
          }
        }
      }
      jobs.forEach { it.get() }

      assert calcCount.get() <= jobs.size()

    }

  }
}
