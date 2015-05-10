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


class GrConstantConditionsTest extends GrConstantConditionsTestBase {

  void "test initialization"() {
    testHighlighting '''
import org.jetbrains.annotations.Nullable
import org.jetbrains.annotations.NotNull

def nullInitialization() {
    @NotNull b = <warning descr="'null' is assigned to a variable that is annotated with @NotNull">null</warning>
}

def nullableInitialization(@Nullable a) {
    @NotNull b = <warning descr="Expression 'a' might evaluate to null but is assigned to a variable that is annotated with @NotNull">a</warning>
}

def unknownInitialization(a) {
    @NotNull b = a
}
'''
  }

  void "test assignments"() {
    testHighlighting '''
import org.jetbrains.annotations.Nullable
import org.jetbrains.annotations.NotNull

def nullAssignment() {
    @NotNull b
    b = <warning descr="'null' is assigned to a variable that is annotated with @NotNull">null</warning>
}

def nullableAssignment(@Nullable a) {
    @NotNull b
    b = <warning descr="Expression 'a' might evaluate to null but is assigned to a variable that is annotated with @NotNull">a</warning>
}

def unknownAssignment(a) {
    @NotNull b
    b = a
}

def "assign not-null to nullable"(@NotNull a) {
    @Nullable b = a
    @NotNull c = b
}

def "assign nullable to unknown"() {
    def a = null
    @NotNull b = <warning descr="Expression 'a' might evaluate to null but is assigned to a variable that is annotated with @NotNull">a</warning>
}
'''
  }

  void "test conditions"() {
    testHighlighting '''
import org.jetbrains.annotations.NotNull

class SomeClassWithMethods {
  def method() {}
}

@NotNull
def ternarySimple(SomeClassWithMethods a) {
    a == null ?
      <warning descr="Method invocation 'a.method()' may produce 'java.lang.NullPointerException'">a.method()</warning>
      : <warning descr="'null' is returned by the method declared as @NotNull">null</warning>
}

@NotNull
def ternary(a) {
    a ? (<warning descr="Condition 'a' is always true">a</warning> ? <warning descr="'null' is returned by the method declared as @NotNull">null</warning> : null)
      : <warning descr="Condition 'a' is always false">a</warning> ? null : <warning descr="'null' is returned by the method declared as @NotNull">null</warning>
}

def ifNullUnknown(o) {
    if (o == null) {
        <warning descr="Method invocation 'o.toString()' may produce 'java.lang.NullPointerException'">o.toString()</warning>
    } else {
        o.hashCode()
    }
}

def ifNotNullUnknown(o) {
    if (o != null) {
        if (!o) {
            o.toString()
        }
    } else {
        <warning descr="Method invocation 'o.hashCode()' may produce 'java.lang.NullPointerException'">o.hashCode()</warning>
    }
}

def ifUnknown(o) {
    if (o) {
        <warning descr="Qualifier 'o' is always not null">o</warning>?.toString()
    } else {
        o.hashCode()
    }
}

def unknownConditions(a) {
    if (a) {
        a.toString()
        if (<warning descr="Condition 'a' is always true">a</warning>) {

        }
        if (<warning descr="Condition '!a' is always false">!a</warning>) {

        }
        if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {

        }
        if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {

        }
        if (a == "a") {

        }
    } else {
        if (<warning descr="Condition 'a' is always false">a</warning>) {

        }
        if (<warning descr="Condition '!a' is always true">!a</warning>) {

        }
        if (a == null) {

        }
        if (a != null) {

        }
        if (a == "a") {

        }
    }
}
'''
  }


  void "test qualifiers"() {
    testHighlighting '''
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

class ClassWithField {
    def field

    def foo() {}
}

def nullableImplicitGetter(@Nullable ClassWithField a) {
    <warning descr="Method invocation 'a.field' may produce 'java.lang.NullPointerException'">a.field</warning>
    a.field // forced @NotNull
}

def notNullImplicitGetter(@NotNull ClassWithField a) {
    a.field
}

def unknownImplicitGetter(ClassWithField a) {
    a.field
}


@NotNull
def safeNullableImplicitGetter(@Nullable ClassWithField a) {
    <warning descr="Expression 'a?.field' might evaluate to null but is returned by the method declared as @NotNull">a?.field</warning>
}

@NotNull
def safeNotNullImplicitGetter(@NotNull ClassWithField a) {
   <warning descr="Qualifier 'a' is always not null">a</warning>?.field
}

@NotNull
def safeUnknownImplicitGetter(ClassWithField a) {
    <warning descr="Expression 'a?.field' might evaluate to null but is returned by the method declared as @NotNull">a?.field</warning>
}


def nullableExplicitGetter(@Nullable ClassWithField a) {
    <warning descr="Method invocation 'a.getField()' may produce 'java.lang.NullPointerException'">a.getField()</warning>
}

def notNullExplicitGetter(@NotNull ClassWithField a) {
    a.getField()
}

def unknownExplicitGetter(ClassWithField a) {
    a.getField()
}


def nullableFieldReference(@Nullable ClassWithField a) {
    <warning descr="Dereference of 'a' may produce 'java.lang.NullPointerException'">a</warning>.@field
}

def notNullFieldReference(@NotNull ClassWithField a) {
    a.@field
}

def unknownFieldReference(ClassWithField a) {
    a.@field
}

@NotNull
def safeNullableFieldReference(@Nullable ClassWithField a) {
    <warning descr="Expression 'a?.@field' might evaluate to null but is returned by the method declared as @NotNull">a?.@field</warning>
}

@NotNull
def safeNotNullFieldReference(@NotNull ClassWithField a) {
    <warning descr="Qualifier 'a' is always not null">a</warning>?.@field
}

@NotNull
def safeUnknownFieldReference(ClassWithField a) {
    <warning descr="Expression 'a?.@field' might evaluate to null but is returned by the method declared as @NotNull">a?.@field</warning>
}

def safeNullGetter(ClassWithField a) {
    if (a == null) {
        <warning descr="Qualifier 'a' is always null">a</warning>?.field
    } else {
        <warning descr="Qualifier 'a' is always not null">a</warning>?.field
    }
}
'''
  }

  void "test simple method call"() {
    testHighlighting '''
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

class ClassWithMethod {
    def foo() {}
}

def nullableMethodCall(@Nullable ClassWithMethod a) {
    <warning descr="Method invocation 'a.foo()' may produce 'java.lang.NullPointerException'">a.foo()</warning>
}

def notNullMethodCall(@NotNull ClassWithMethod a) {
    a.foo()
}

def unknownMethodCall(ClassWithMethod a) {
    a.foo()
}
'''
  }

  void "test safe method call"() {
    testHighlighting '''
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

class ClassWithMethod {
    def foo() {}
}

@NotNull
def safeNullableMethodCall(@Nullable ClassWithMethod a) {
    <warning descr="Expression 'a?.foo()' might evaluate to null but is returned by the method declared as @NotNull">a?.foo()</warning>
}

@NotNull
def safeNotNullMethodCall(@NotNull ClassWithMethod a) {
    <warning descr="Qualifier 'a' is always not null">a</warning>?.foo()
}

@NotNull
def safeUnknownMethodCall(ClassWithMethod a) {
    <warning descr="Expression 'a?.foo()' might evaluate to null but is returned by the method declared as @NotNull">a?.foo()</warning>
}
'''
  }


  void "test field initializers and method parameters"() {
    testHighlighting '''
import org.jetbrains.annotations.Contract
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

class A {

    @NotNull
    static field = <warning descr="'null' is assigned to a variable that is annotated with @NotNull">null</warning>

    @NotNull
    def field0 = method0()

    @NotNull
    def field1 = <warning descr="Expression 'method1()' might evaluate to null but is assigned to a variable that is annotated with @NotNull">method1()</warning>

    @NotNull
    def field2 = method2()
    
    @NotNull
    def field3 = contractMethod(null)

    static method0(@NotNull a = <warning descr="'null' is assigned to a variable that is annotated with @NotNull">null</warning>, @NotNull b = field, c = []) {}

    @Nullable
    static method1(@NotNull a = []) {}

    @NotNull
    static method2() {}
    
    @Contract("null -> !null")
    static contractMethod(@Nullable a) {}
}
'''
  }

  void "test literals are not null"() {
    testHighlighting '''
def testList() {
    if (<warning descr="Condition '[] == null' is always false">[] == null</warning>) {}
    if (<warning descr="Condition '[] != null' is always true">[] != null</warning>) {}
    if (<warning descr="Condition '[1,2] == null' is always false">[1,2] == null</warning>) {}
    if (<warning descr="Condition '[1,2] != null' is always true">[1,2] != null</warning>) {}
}

def testMap() {
    if (<warning descr="Condition '[:] == null' is always false">[:] == null</warning>) {}
    if (<warning descr="Condition '[:] != null' is always true">[:] != null</warning>) {}
    if (<warning descr="Condition '[a:1, b:2] == null' is always false">[a:1, b:2] == null</warning>) {}
    if (<warning descr="Condition '[a:1, b:2] !=null' is always true">[a:1, b:2] !=null</warning>) {}
}

def testClosure() {
    if (<warning descr="Condition '{} == null' is always false">{} == null</warning>) {}
    if (<warning descr="Condition '{} != null' is always true">{} != null</warning>) {}
    if (<warning descr="Condition '({}) == null' is always false">({}) == null</warning>) {}
    if (<warning descr="Condition '({}) != null' is always true">({}) != null</warning>) {}
}
'''
  }

  void "test not null parameter"() {
    testHighlighting '''
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

def methodWithNotNullParameter(@NotNull a) {}

def passingNullToNotNull() {
    methodWithNotNullParameter(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>)
}

def passingNullableToNotNull(@Nullable a) {
    methodWithNotNullParameter(<warning descr="Argument 'a' might be null">a</warning>)
}

def passingNotNullToNotNull(@NotNull a) {
    methodWithNotNullParameter(a)
}

def passingUnknownToNotNull(a) {
    methodWithNotNullParameter(a)
}

def passingClosureToNotNull() {
    methodWithNotNullParameter { -> }
}
'''
  }

  void "test not annotated parameter"() {
    testHighlighting '''
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

def methodWithNotAnnotatedParameter(a) {}

def passingNullToNotAnnotated() {
    methodWithNotAnnotatedParameter(<warning descr="Passing 'null' argument to non annotated parameter">null</warning>)
}

def passingNullableToNotAnnotated(@Nullable a) {
    methodWithNotAnnotatedParameter(<warning descr="Argument 'a' might be null but passed to non annotated parameter">a</warning>)
}

def passingNotNullToNotAnnotated(@NotNull a) {
    methodWithNotAnnotatedParameter(a)
}
'''
  }
}
