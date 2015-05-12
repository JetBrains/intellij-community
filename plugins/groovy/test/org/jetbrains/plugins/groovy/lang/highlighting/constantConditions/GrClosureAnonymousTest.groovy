/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.highlighting.constantConditions


class GrClosureAnonymousTest extends GrConstantConditionsTestBase {
  void "test closures"() {
    testHighlighting '''
def testAssignNullClosure() {
    def a = new Object()
    if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
    if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}

    def c = {
        a = null
    }

    if (a == null) {}
    if (a != null) {}
}

def testAssignNotNullClosure() {
    def a = null
    if (<warning descr="Condition 'a == null' is always true">a == null</warning>) {}
    if (<warning descr="Condition 'a != null' is always false">a != null</warning>) {}

    def c = {
        a = new Object()
    }

    if (a == null) {}
    if (a != null) {}
}

def testEach() {
    def a = new Object()
    if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
    if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}

    [].each {
        a = null
    }

    if (a == null) {}
    if (a != null) {}
}
'''
  }

  void "test state within closure"() {
    testHighlighting '''
def stateWithinClosure(a) {
    if (a == null) {
        def c = {
            if (<warning descr="Condition 'a == null' is always true">a == null</warning>) {}
            if (<warning descr="Condition 'a != null' is always false">a != null</warning>) {}
            if (<warning descr="Condition 'a' is always false">a</warning>) {}
            if (<warning descr="Condition '!a' is always true">!a</warning>) {}
        }
    } else {
        def c = {
            if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
            if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
            if (a) {}
            if (!a) {}
        }
    }
}
'''
  }

  void "test anonymous class"() {
    testHighlighting '''
def a = new Object()
if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}

def c = new Runnable() {
    void run() {
        a = null
    }
}

if (a == null) {}
if (a != null) {}
'''
  }
}
