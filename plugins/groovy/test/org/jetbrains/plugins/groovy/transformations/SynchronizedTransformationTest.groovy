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
package org.jetbrains.plugins.groovy.transformations

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.GroovyUnusedDeclarationInspection
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField

@CompileStatic
class SynchronizedTransformationTest extends LightGroovyTestCase {

  LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

  void 'test $lock is used by annotated instance method'() {
    testHighlighting '''\
final $lock = new Object()
@Synchronized
def foo() {}
'''
  }

  void 'test $lock is not used by annotated static method'() {
    testHighlighting '''\
final <warning descr="Property $lock is unused">$lock</warning> = new Object()
@Synchronized
static foo() {}
'''
  }

  void 'test static $lock is used by annotated instance method'() {
    testHighlighting '''\
static final <error descr="Lock field '$lock' must not be static">$lock</error> = new Object()
@Synchronized
def foo() {}
'''
  }

  void 'test $LOCK is used by annotated static method'() {
    testHighlighting '''\
static final $LOCK = new Object()
@Synchronized
static foo() {}
'''
  }

  void 'test $LOCK is not used by annotated instance method'() {
    testHighlighting '''\
static final <warning descr="Property $LOCK is unused">$LOCK</warning> = new Object()
@Synchronized
def foo() {}
'''
  }

  void 'test instance $LOCK is used by annotated static method'() {
    testHighlighting '''\
final <error descr="Lock field '$LOCK' must be static">$LOCK</error> = new Object()
@Synchronized
static foo() {}
'''
  }

  void 'test lock not found'() {
    testHighlighting '''\
@Synchronized("<error descr="Lock field 'myLock' not found">myLock</error>")
def foo() {}
'''
  }

  void 'test custom instance lock is used by instance method'() {
    testHighlighting '''\
final myLock = new Object()
@Synchronized("myLock")
def foo() {}
'''
  }

  void 'test custom instance lock is used by static method'() {
    testHighlighting '''\
final <error descr="Lock field 'myLock' must be static">myLock</error> = new Object()
@Synchronized("myLock")
static foo() {}
'''
  }

  void 'test custom static lock is used by static method'() {
    testHighlighting '''\
static final myStaticLock = new Object()
@Synchronized("myStaticLock")
static foo() {}
'''
  }

  void 'test custom static lock is used by instance method'() {
    testHighlighting '''\
static final myStaticLock = new Object()
@Synchronized("myStaticLock")
def foo() {}
'''
  }

  void 'test not allowed on abstract methods'() {
    testHighlighting '''\
import groovy.transform.Synchronized

class C {
  @Synchronized
  def foo() {}
}

interface I {
  <error descr="@Synchronized not allowed on abstract method">@Synchronized</error>
  def foo()
}

abstract class AC {
  <error descr="@Synchronized not allowed on abstract method">@Synchronized</error>
  abstract def foo()
  def bar() {}
}

trait T {
  <error descr="@Synchronized not allowed on abstract method">@Synchronized</error>
  abstract def foo()
  @Synchronized
  def bar() {}
}
''', new InspectionProfileEntry[0]
  }


  void 'test resolve to field'() {
    fixture.with {
      configureByText 'a.groovy', '''\
class A {
  final myLock = new Object()
  @groovy.transform.Synchronized("myL<caret>ock")
  def foo() {}
}
'''
      def ref = file.findReferenceAt(editor.caretModel.offset)
      assert ref
      assert ref.resolve() instanceof GrField
    }
  }

  void 'test complete fields'() {
    fixture.with {
      configureByText 'a.groovy', '''\
class A {
final foo = 1, bar = 2, baz = 3
@groovy.transform.Synchronized("<caret>")
def m() {}
}
'''
      completeBasic()
      assert lookupElementStrings.containsAll(['foo', 'bar', 'baz'])
    }
  }

  void 'test rename custom lock'() {
    testRename '''\
final myLock = new Object()
@Synchronized("myL<caret>ock")
def foo() {}
''', "myLock2", '''\
final myLock2 = new Object()
@Synchronized("myLock2")
def foo() {}
'''
  }

  void 'test rename $lock'() {
    testRename '''\
final $l<caret>ock = new Object()
@Synchronized
def foo() {}
@Synchronized('$lock')
def foo2() {}
''', "myLock", '''\
final myLock = new Object()
@Synchronized('myLock')
def foo() {}
@Synchronized('myLock')
def foo2() {}
'''
  }

  private testHighlighting(String text) {
    testHighlighting """\
import groovy.transform.Synchronized
class A {
$text
}
new A().foo()
""", [
      new GroovyUnusedDeclarationInspection(),
      new UnusedDeclarationInspectionBase()
    ] as InspectionProfileEntry[]
  }

  private testHighlighting(String text, InspectionProfileEntry[] inspections) {
    fixture.with {
      configureByText '_.groovy', text
      enableInspections inspections
      checkHighlighting()
    }
  }

  private testRename(String before, String newName, String after) {
    fixture.with {
      configureByText '_.groovy', """\
import groovy.transform.Synchronized
class A {
$before
}
"""
      renameElementAtCaret(newName)
      checkResult """\
import groovy.transform.Synchronized
class A {
$after
}
"""
    }
  }
}