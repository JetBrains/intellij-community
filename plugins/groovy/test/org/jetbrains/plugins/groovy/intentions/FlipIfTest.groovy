/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
class FlipIfTest extends GrIntentionTestCase {
  FlipIfTest() {
    super(GroovyIntentionsBundle.message('flip.if.intention.name'))
  }

  void test0() {
    doTextTest('''\
i<caret>f (abc) {
  print 1
}
else if (cde) {
  print 2
}
''', '''\
i<caret>f (cde) {
  print 2
}
else if (abc) {
  print 1
}
''')
  }

  void test1() {
    doTextTest('''\
if (abc) {
  print 1
}
else i<caret>f (cde) {
  print 2
}
else if (xyz) {
  print 3
}
''', '''\
if (abc) {
  print 1
}
else i<caret>f (xyz) {
  print 3
}
else if (cde) {
  print 2
}
''')
  }

}
