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
package org.jetbrains.plugins.groovy.lang.surroundWith

import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * User: Dmitry.Krasilschikov
 */


class SurroundStatementsTest extends SurroundTestCase {

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/surround/statements/"
  }

  void testClosure1() throws Exception { doTest(new SurrounderByClosure()) }

  void testClosure2() throws Exception { doTest(new SurrounderByClosure()) }

  void testClosure3() throws Exception { doTest(new SurrounderByClosure()) }

  void testIf1() throws Exception { doTest(new IfSurrounder()) }

  void testIf_else1() throws Exception { doTest(new IfElseSurrounder()) }

  void testShouldFailWithType() throws Exception { doTest(new ShouldFailWithTypeStatementsSurrounder()) }

  void testTry_catch1() throws Exception { doTest(new TryCatchSurrounder()) }

  void testTry_catch_finally() throws Exception { doTest(new TryCatchFinallySurrounder()) }

  void testTry_finally1() throws Exception { doTest(new TryFinallySurrounder()) }

  void testTry_finallyFormatting() throws Exception { doTest(new TryFinallySurrounder()) }

  void testWhile1() throws Exception { doTest(new WhileSurrounder()) }

  void testWith2() throws Exception { doTest(new WithStatementsSurrounder()) }

  void testFor1() throws Exception { doTest(new ForSurrounder()) }

  void testIfComments() throws Exception { doTest(new IfSurrounder()) }

  void testBracesInIf() {
    doTest(new GrBracesSurrounder(), '''\
if (abc)
    pr<caret>int 'abc'
''', '''\
if (abc) {
    print 'abc'
}
''')
  }

  void testBracesInWhile() {
    doTest(new GrBracesSurrounder(), '''\
while (true)
    print 'en<caret>dless'
''' , '''\
while (true) {
    print 'endless'
}
''')
  }

  void testBraces() {
    doTest(new GrBracesSurrounder(), '''\
print 2
pri<caret>nt 3
print 4
''', '''\
print 2
{ print 3 }
print 4
''')
  }
}
