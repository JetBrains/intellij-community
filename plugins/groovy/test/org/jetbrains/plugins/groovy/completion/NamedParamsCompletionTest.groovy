// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors

class NamedParamsCompletionTest extends GroovyCompletionTestBase {
  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_2_5

  void testWithSetter() {
    doCompletionTest('''\
import groovy.transform.NamedDelegate
import groovy.transform.NamedVariant

class E {
    private int a
    void setParam(int tt){}
}

@NamedVariant
String foo(@NamedDelegate E e) {
    null
}

foo(par<caret>)
''', '''\
import groovy.transform.NamedDelegate
import groovy.transform.NamedVariant

class E {
    private int a
    void setParam(int tt){}
}

@NamedVariant
String foo(@NamedDelegate E e) {
    null
}

foo(param: <caret>)
''', CompletionType.BASIC)
  }

  void testWithJava() {
    myFixture.configureByText('A.java', """
class A {
  public void setParam(int tt){}
}
""")
    doCompletionTest('''\
import groovy.transform.NamedDelegate
import groovy.transform.NamedVariant

@NamedVariant
String foo(@NamedDelegate A a) {
    null
}

foo(par<caret>)
''', '''\
import groovy.transform.NamedDelegate
import groovy.transform.NamedVariant

@NamedVariant
String foo(@NamedDelegate A a) {
    null
}

foo(param: <caret>)
''', CompletionType.BASIC)
  }

  void testWithSeveralVariants() {
    doVariantableTest('''\
import groovy.transform.NamedDelegate
import groovy.transform.NamedVariant

class E {
    int param1
    void setParam2(int tt){}
}

@NamedVariant
String foo(@NamedDelegate E e) {
    null
}

foo(par<caret>)
''', CompletionType.BASIC, "param1", "param2")
  }

  void testWithNamedParams() {
    myFixture.addClass('''\
package com.foo;
public class MyCoolClass {}
''')
    doVariantableTest('''\
import groovy.transform.NamedParam
import groovy.transform.NamedVariant
import com.foo.MyCoolClass

@NamedVariant
String foo(@NamedParam int param1, @NamedParam MyCoolClass param2) {
    null
}

foo(par<caret>)
''', CompletionType.BASIC, "param1", "param2")
  }

  void testNamedParamsWithValue() {
    myFixture.addClass('''\
package com.foo;
public class MyCoolClass {}
''')
    doCompletionTest'''\
import groovy.transform.NamedParam
import groovy.transform.NamedVariant
import com.foo.MyCoolClass

@NamedVariant
String foo(@NamedParam("larch1") int param1, @NamedParam("param2") MyCoolClass larch2) {
    null
}

foo(lar<caret>)
''', '''\
import groovy.transform.NamedParam
import groovy.transform.NamedVariant
import com.foo.MyCoolClass

@NamedVariant
String foo(@NamedParam("larch1") int param1, @NamedParam("param2") MyCoolClass larch2) {
    null
}

foo(larch1: <caret>)
''',
    CompletionType.BASIC
  }
}
