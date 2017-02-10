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
package org.jetbrains.plugins.groovy.intentions

/**
 * @author Max Medvedev
 */
class GrSplitDeclarationTest extends GrIntentionTestCase {

  void testSingleVar() {
    doTextTest('''\
def abc = 5
''', GroovyIntentionsBundle.message('split.into.declaration.and.assignment'), '''\
def abc
abc = 5
''')
  }

  void testMultiVar() {
    doTextTest('''\
def abc = 5, cde = 7
''', GroovyIntentionsBundle.message('split.into.separate.declaration'), '''\
def abc = 5
def cde = 7
''')
  }

  void testTupleAssignment() {
    doTextTest('''\
def (abc, cde) = foo()
''', GroovyIntentionsBundle.message('split.into.declaration.and.assignment'), '''\
def (abc, cde)
(abc, cde) = foo()
''')
  }

  void testSimpleTupleAssignment() {
    doTextTest('''\
def (abc, cde) = [1, 2]
''', GroovyIntentionsBundle.message('split.into.separate.declaration'), '''\
def abc = 1
def cde = 2
''')
  }

  void testSimpleTupleAssignmentWithExplicitTypes() {
    doTextTest('''\
def (int abc, int cde) = [1, 2]
''', GroovyIntentionsBundle.message('split.into.separate.declaration'), '''\
int abc = 1
int cde = 2
''')
  }

}
