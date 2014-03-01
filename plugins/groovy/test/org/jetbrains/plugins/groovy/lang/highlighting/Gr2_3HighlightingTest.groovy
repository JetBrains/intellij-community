/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection

/**
 * Created by Max Medvedev on 27/02/14
 */
class Gr2_3HighlightingTest extends GrHighlightingTestBase {
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return GroovyLightProjectDescriptor.GROOVY_2_3
  }

  @Override
  InspectionProfileEntry[] getCustomInspections() {
    [new GroovyAssignabilityCheckInspection(), new GrUnresolvedAccessInspection()]
  }

  void assertScript(String text) {
    testHighlighting("import groovy.transform.CompileStatic\nimport groovy.transform.stc.ClosureParams\n" + text)
  }

  void shouldFailWithMessages(String text) {
    assertScript(text)
  }

  void testInferenceOnNonExtensionMethod() {
    assertScript '''
import groovy.transform.stc.FirstParam

public <T> T foo(T arg, @ClosureParams(FirstParam) Closure c) { c.call(arg) }

@CompileStatic
def test() {
    assert foo('a') { it.toUpperCase() } == 'A'
}
'''
  }

  void testFromStringWithSimpleType() {
    assertScript '''
import groovy.transform.stc.FromString

void foo(@ClosureParams(value=FromString,options="java.lang.String") Closure cl) { cl.call('foo') }

@CompileStatic
def test() {
    foo { String str -> println str.toUpperCase()}
}
'''

    shouldFailWithMessages '''
import groovy.transform.stc.FromString

void foo(@ClosureParams(value=FromString,options="java.lang.String") Closure cl) { cl.call('foo') }

@CompileStatic
def test() {
    foo { <error descr="Expected String">Date</error> str -> println str}
}
'''
  }

  void testFromStringWithGenericType() {
    assertScript '''
import groovy.transform.stc.FromString

void foo(@ClosureParams(value=FromString,options="java.util.List<java.lang.String>") Closure cl) { cl.call(['foo']) }

@CompileStatic
def test() {
    foo { List<String> str -> str.each { println it.toUpperCase() } }
}
'''

    shouldFailWithMessages '''
import groovy.transform.stc.FromString

void foo(@ClosureParams(value=FromString,options="java.util.List<java.lang.String>") Closure cl) { cl.call(['foo']) }

@CompileStatic
def test() {
    foo { <error descr="Expected List<String>">List<Date></error> d -> d.each { println it } }
}
'''
  }

  void testFromStringWithDirectGenericPlaceholder() {

    assertScript '''
import groovy.transform.stc.FromString

public <T> void foo(T t, @ClosureParams(value=FromString,options="T") Closure cl) { cl.call(t) }

@CompileStatic
def test() {
    foo('hey') { println it.toUpperCase() }
}
'''

  }

  void testFromStringWithGenericPlaceholder() {
    assertScript '''
import groovy.transform.stc.FromString

public <T> void foo(T t, @ClosureParams(value=FromString,options="java.util.List<T>") Closure cl) { cl.call([t,t]) }

@CompileStatic
def test() {
    foo('hey') { List<String> str -> str.each { println it.toUpperCase() } }
}
'''

  }

  void testFromStringWithGenericPlaceholderFromClass() {
    assertScript '''
import groovy.transform.stc.FromString

class Foo<T> {
    public void foo(@ClosureParams(value=FromString,options="java.util.List<T>") Closure cl) { cl.call(['hey','ya']) }
}

@CompileStatic
def test() {
    def foo = new Foo<String>()

    foo.foo { List<String> str -> str.each { println it.toUpperCase() } }
}'''
  }

  void testFromStringWithGenericPlaceholderFromClassWithTwoGenerics() {
    assertScript '''
import groovy.transform.stc.FromString

class Foo<T,U> {
    public void foo(@ClosureParams(value=FromString,options="java.util.List<U>") Closure cl) { cl.call(['hey','ya']) }
}

@CompileStatic
def test() {
    def foo = new Foo<Integer,String>()

    foo.foo { List<String> str -> str.each { println it.toUpperCase() } }
}'''
  }

  void testFromStringWithGenericPlaceholderFromClassWithTwoGenericsAndNoExplicitSignature() {
    assertScript '''
import groovy.transform.stc.FromString

class Foo<T,U> {
    public void foo(@ClosureParams(value=FromString,options="java.util.List<U>") Closure cl) { cl.call(['hey','ya']) }
}

@CompileStatic
def test() {
    def foo = new Foo<Integer,String>()

    foo.foo { it.each { println it.toUpperCase() } }
}'''
  }

  void testFromStringWithGenericPlaceholderFromClassWithTwoGenericsAndNoExplicitSignatureAndNoFQN() {
    assertScript '''
import groovy.transform.stc.FromString

class Foo<T,U> {
    public void foo(@ClosureParams(value=FromString,options="List<U>") Closure cl) { cl.call(['hey','ya']) }
}

@CompileStatic
def test() {
    def foo = new Foo<Integer,String>()

    foo.foo { it.each { println it.toUpperCase() } }
}'''
  }

  void testFromStringWithGenericPlaceholderFromClassWithTwoGenericsAndNoExplicitSignatureAndNoFQNAndReferenceToSameUnitClass() {
    assertScript '''
import groovy.transform.stc.FromString

class Foo {
    void bar() {
        println 'Haha!'
    }
}

class Tor<D,U> {
    public void foo(@ClosureParams(value=FromString,options="List<U>") Closure cl) { cl.call([new Foo(), new Foo()]) }
}

@CompileStatic
def test() {
    def tor = new Tor<Integer,Foo>()

    tor.foo { it.each { it.bar() } }
}'''
  }

  void testFromStringWithGenericPlaceholderFromClassWithTwoGenericsAndNoExplicitSignatureAndNoFQNAndReferenceToSameUnitClassAndTwoArgs() {
    assertScript '''
import groovy.transform.stc.FromString

class Foo {
    void bar() {
        println 'Haha!'
    }
}

class Tor<D,U> {
    public void foo(@ClosureParams(value=FromString,options=["D,List<U>"]) Closure cl) { cl.call(3, [new Foo(), new Foo()]) }
}

@CompileStatic
def test() {

    def tor = new Tor<Integer,Foo>()

    tor.foo { r, e -> r.times { e.each { it.bar() } } }
}
'''
  }

  void testFromStringWithGenericPlaceholderFromClassWithTwoGenericsAndPolymorphicSignature() {
    assertScript '''
import groovy.transform.stc.FromString

class Foo {
    void bar() {
        println 'Haha!'
    }
}

class Tor<D,U> {
    public void foo(@ClosureParams(value=FromString,options=["D,List<U>", "D"]) Closure cl) {
        if (cl.maximumNumberOfParameters==2) {
            cl.call(3, [new Foo(), new Foo()])
        } else {
            cl.call(3)
        }
    }
}

@CompileStatic
def test() {
    def tor = new Tor<Integer,Foo>()

    tor.foo { r, e -> r.times { e.each { it.bar() } } }
    tor.foo { it.times { println 'polymorphic' } }
}
'''
  }
}
