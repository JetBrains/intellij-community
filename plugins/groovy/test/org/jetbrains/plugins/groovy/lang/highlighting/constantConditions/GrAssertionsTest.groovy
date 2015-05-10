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

class GrAssertionsTest extends GrConstantConditionsTestBase {

  void "test unknown assertions"() {
    testHighlighting '''
import org.jetbrains.annotations.NotNull

class SomeException extends Throwable {}

@NotNull
def assertUnknownNull(a) {
    try {
        assert a == null
        if (<warning descr="Condition 'a' is always false">a</warning>) {}
        if (<warning descr="Condition '!a' is always true">!a</warning>) {}
        if (<warning descr="Condition 'a == null' is always true">a == null</warning>) {}
        if (<warning descr="Condition 'a != null' is always false">a != null</warning>) {}
    } catch (AssertionError ignored) {
        if (a) {}
        if (!a) {}
        if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
        if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
    } catch (SomeException e) {
        if (a) {}
        if (!a) {}
        if (a == null) {}
        if (a != null) {}
    }
}

@NotNull
def assertUnknownNotNull(a) {
    try {
        assert a != null
        if (a) {}
        if (!a) {}
        if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
        if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
    } catch (AssertionError ignored) {
        if (<warning descr="Condition 'a' is always false">a</warning>) {}
        if (<warning descr="Condition '!a' is always true">!a</warning>) {}
        if (<warning descr="Condition 'a == null' is always true">a == null</warning>) {}
        if (<warning descr="Condition 'a != null' is always false">a != null</warning>) {}
    } catch (SomeException e) {
        if (a) {}
        if (!a) {}
        if (a == null) {}
        if (a != null) {}
    }
}

@NotNull
def assertUnknownCondition(a) {
    try {
        assert a
        if (<warning descr="Condition 'a' is always true">a</warning>) {}
        if (<warning descr="Condition '!a' is always false">!a</warning>) {}
        if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
        if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
    } catch (AssertionError ignored) {
        if (<warning descr="Condition 'a' is always false">a</warning>) {}
        if (<warning descr="Condition '!a' is always true">!a</warning>) {}
        if (a == null) {}
        if (a != null) {}
    } catch (SomeException e) {
        if (a) {}
        if (!a) {}
        if (a == null) {}
        if (a != null) {}
    }
}

@NotNull
def assertUnknownNotCondition(a) {
    try {
        assert !a
        if (<warning descr="Condition 'a' is always false">a</warning>) {}
        if (<warning descr="Condition '!a' is always true">!a</warning>) {}
        if (a == null) {}
        if (a != null) {}
    } catch (AssertionError ignored) {
        if (<warning descr="Condition 'a' is always true">a</warning>) {}
        if (<warning descr="Condition '!a' is always false">!a</warning>) {}
        if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
        if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
    } catch (SomeException e) {
        if (a) {}
        if (!a) {}
        if (a == null) {}
        if (a != null) {}
    }
}
'''
  }

  void "test not null assertions"() {
    testHighlighting '''
import org.jetbrains.annotations.NotNull

@NotNull
def assertNotNullNull(@NotNull a) {
    try {
        assert <warning descr="Condition 'a == null' is always false">a == null</warning>
        if (a) {}
        if (!a) {}
        if (a == null) {}
        if (a != null) {}
    } catch (AssertionError ignored) {
        if (a) {}
        if (!a) {}
        if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
        if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
    }
}

def assertNotNullNotNull(@NotNull a) {
    try {
        assert <warning descr="Condition 'a != null' is always true">a != null</warning>
        if (a) {}
        if (!a) {}
        if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
        if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
    } catch (AssertionError ignored) {
        if (a) {}
        if (!a) {}
        if (a == null) {}
        if (a != null) {}
    }
}

def assertNotNullCondition(@NotNull a) {
    try {
        assert a
        if (<warning descr="Condition 'a' is always true">a</warning>) {}
        if (<warning descr="Condition '!a' is always false">!a</warning>) {}
        if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
        if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
    } catch (AssertionError ignored) {
        if (<warning descr="Condition 'a' is always false">a</warning>) {}
        if (<warning descr="Condition '!a' is always true">!a</warning>) {}
        if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
        if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
    }
}

def assertNotNullNotCondition(@NotNull a) {
    try {
        assert !a
        if (<warning descr="Condition 'a' is always false">a</warning>) {}
        if (<warning descr="Condition '!a' is always true">!a</warning>) {}
        if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
        if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
    } catch (AssertionError ignored) {
        if (<warning descr="Condition 'a' is always true">a</warning>) {}
        if (<warning descr="Condition '!a' is always false">!a</warning>) {}
        if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
        if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
    }
}
'''
  }

  void "test nullable assertions"() {
    testHighlighting '''

import org.jetbrains.annotations.Nullable

def assertNullableNull(@Nullable a) {
    try {
        assert a == null
        if (<warning descr="Condition 'a' is always false">a</warning>) {}
        if (<warning descr="Condition '!a' is always true">!a</warning>) {}
        if (<warning descr="Condition 'a == null' is always true">a == null</warning>) {}
        if (<warning descr="Condition 'a != null' is always false">a != null</warning>) {}
    } catch (AssertionError ignored) {
        if (a) {}
        if (!a) {}
        if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
        if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
    }
}

def assertNullableNotNull(@Nullable a) {
    try {
        assert a != null
        if (a) {}
        if (!a) {}
        if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
        if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
    } catch (AssertionError ignored) {
        if (<warning descr="Condition 'a' is always false">a</warning>) {}
        if (<warning descr="Condition '!a' is always true">!a</warning>) {}
        if (<warning descr="Condition 'a == null' is always true">a == null</warning>) {}
        if (<warning descr="Condition 'a != null' is always false">a != null</warning>) {}
    }
}

def assertNullableCondition(@Nullable a) {
    try {
        assert a
        if (<warning descr="Condition 'a' is always true">a</warning>) {}
        if (<warning descr="Condition '!a' is always false">!a</warning>) {}
        if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
        if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
    } catch (AssertionError ignored) {
        if (<warning descr="Condition 'a' is always false">a</warning>) {}
        if (<warning descr="Condition '!a' is always true">!a</warning>) {}
        if (a == null) {}
        if (a != null) {}
    }
}


def assertNullableNotCondition(@Nullable a) {
    try {
        assert !a
        if (<warning descr="Condition 'a' is always false">a</warning>) {}
        if (<warning descr="Condition '!a' is always true">!a</warning>) {}
        if (a == null) {}
        if (a != null) {}
    } catch (AssertionError ignored) {
        if (<warning descr="Condition 'a' is always true">a</warning>) {}
        if (<warning descr="Condition '!a' is always false">!a</warning>) {}
        if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
        if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
    }
}
'''
  }
}
