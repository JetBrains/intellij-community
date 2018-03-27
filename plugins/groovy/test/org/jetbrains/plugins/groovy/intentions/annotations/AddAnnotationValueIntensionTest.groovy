// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.annotations

import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.intentions.GrIntentionTestCase

@CompileStatic
class AddAnnotationValueIntensionTest extends GrIntentionTestCase {

  AddAnnotationValueIntensionTest() {
    super(AddAnnotationValueIntention)
  }

  void 'test add value'() {
    doTextTest '''\
@Anno(<caret>arg)
def foo() {}
''', '''\
@Anno(<caret>value = arg)
def foo() {}
'''
  }

  void 'test add value inner'() {
    doTextTest '''\
@Anno(@Inner(<caret>arg))
def foo() {}
''', '''\
@Anno(@Inner(<caret>value = arg))
def foo() {}
'''
  }

  void 'test add value inner annotation'() {
    doTextTest '''\
@Anno(<caret>@Inner(arg))
def foo() {}
''', '''\
@Anno(<caret>value = @Inner(arg))
def foo() {}
'''
  }

  void 'test dont add value if name defined'() {
    doAntiTest '''\
@Anno(name = <caret>arg)
def foo() {}
'''
  }

  void 'test dont add value if many arguments'() {
    doAntiTest '''\
@Anno(<caret>arg1, arg2)
def foo() {}
'''
  }
}
