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


import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author Max Medvedev
 */
class GrBreakStringOnLineBreaksTest extends GrIntentionTestCase {
  GrBreakStringOnLineBreaksTest() {
    super(GroovyIntentionsBundle.message('gr.break.string.on.line.breaks.intention.name'))
  }

  final String basePath = TestUtils.testDataPath + "intentions/breakStringOnLineBreaks/"

  void testSimple() {
    doTextTest('''print 'ab<caret>c\\ncde\'''', '''\
print 'abc\\n' +
        'cde\'''')
  }

  void testGString() {
    doTextTest('''print "a<caret>\\n$x bc\\n"''', '''\
print "a\\n" +
        "$x bc\\n"''')
  }
}
