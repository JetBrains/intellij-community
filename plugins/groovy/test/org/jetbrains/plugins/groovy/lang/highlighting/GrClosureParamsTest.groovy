/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection

@CompileStatic
class GrClosureParamsTest extends GrHighlightingTestBase {
  final String basePath = super.basePath + 'closureParams/'
  final LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST


  void 'test from string with non-fqn options no error'() {
    addBigInteger()
    doTest('fromString/nonFqnOptions')
  }

  void 'test from string do not resolve usage context'() {
    doTest('fromString/doNotResolveUsageContext', 'com/foo/bar/test.groovy')
  }

  void 'test from string do not resolve usage import context'() {
    doTest('fromString/doNotResolveUsageImportContext', 'com/foo/bar/test.groovy')
  }

  void 'test from string do not resolve method context'() {
    doTest('fromString/doNotResolveMethodContext')
  }

  void 'test from string resolve default package' () {
    doTest('fromString/resolveDefaultPackage')
  }

  void 'test from string resolve default package generic'() {
    myFixture.addClass '''
package com.foo.baz;

import groovy.lang.Closure;
import groovy.transform.stc.ClosureParams;
import groovy.transform.stc.FromString;

public class A<T> {
    public void bar(@ClosureParams(value = FromString.class, options = "MyClass<T>") Closure c) {}
}
'''
    myFixture.addClass '''
public class MyClass<U> {}
'''
    myFixture.configureByText 'a.groovy', '''
import com.foo.baz.A
import groovy.transform.CompileStatic

@CompileStatic
class Usage {
    def foo() { new A<Integer>().bar { <error descr="Expected 'MyClass<java.lang.Integer>', found 'MyClass<java.lang.String>'">MyClass<String></error> a -> } }
}
'''
    myFixture.checkHighlighting()
  }


  void 'test cast argument'() {
    myFixture.configureByText 'a.groovy', '''
import groovy.transform.CompileStatic

@CompileStatic
def m() {
    List<String> l = []
    l.findAll {
        Object o -> false
    }
}
'''
    myFixture.checkHighlighting()
  }


  void 'test cast argument error'() {
    myFixture.configureByText 'a.groovy', '''
import groovy.transform.CompileStatic

@CompileStatic
def m() {
    List<Object> l = []
    l.findAll {
        <error>Integer</error> o -> false
    }
}
'''
    myFixture.checkHighlighting()
  }

  void 'test several signatures'() {
    myFixture.configureByText 'a.groovy', '''
import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

def m1(String o, @ClosureParams(value=SimpleType.class, options="java.lang.String") Closure c) {}
def m1(Double o, @ClosureParams(value=SimpleType.class, options="java.lang.Double") Closure c) {}

@CompileStatic
def m() {
    def a;
    m1(a) {
        double d -> println(d)
    }
}
'''
    myFixture.checkHighlighting()
  }


  void 'test several signatures error'() {
    myFixture.configureByText 'a.groovy', '''
import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

def m1(String o, @ClosureParams(value=SimpleType.class, options="java.lang.String") Closure c) {}
def m1(Double o, @ClosureParams(value=SimpleType.class, options="java.lang.Double") Closure c) {}

@CompileStatic
def m() {
    def a;
    m1(a) {
        <error>long l</error> -> println(l)
    }
}
'''
    myFixture.checkHighlighting()
  }

  void 'test cast several arguments'() {
    myFixture.configureByText 'a.groovy', '''
import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

void forEachTyped(Map<String, Integer> self, @ClosureParams(value=SimpleType.class, options=["java.lang.String", "java.lang.Integer"]) Closure closure) {
    self.each {
        k, v -> closure(k, v)
    };
}

@CompileStatic
def m() {
    Map<String,Integer> m = [:]
    forEachTyped(m) {
        String key, <error>String</error> value -> println( key + ' ' + value)
    }
}
'''
    myFixture.checkHighlighting()
  }

  private void doTest(String dir = getTestName(true), String path = 'test.groovy') {
    myFixture.enableInspections(new GroovyAssignabilityCheckInspection(), new GrUnresolvedAccessInspection())
    myFixture.copyDirectoryToProject(dir, '.')
    def file = myFixture.findFileInTempDir(path)
    myFixture.allowTreeAccessForAllFiles()
    myFixture.configureFromExistingVirtualFile(file)
    myFixture.checkHighlighting()
  }
}
