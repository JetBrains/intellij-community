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
package org.jetbrains.plugins.groovy.lang.surroundWith;

import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * User: Dmitry.Krasilschikov
 * Date: 01.06.2007
 */


public class SurroundStatementsTest extends SurroundTestCase {

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/surround/statements/";
  }

  public void testClosure1() throws Exception { doTest(new SurrounderByClosure()); }
  public void testClosure2() throws Exception { doTest(new SurrounderByClosure()); }
  public void testClosure3() throws Exception { doTest(new SurrounderByClosure()); }
  public void testIf1() throws Exception { doTest(new IfSurrounder()); }
  public void testIf_else1() throws Exception { doTest(new IfElseSurrounder()); }
  public void testShouldFailWithType() throws Exception { doTest(new ShouldFailWithTypeStatementsSurrounder()); }
  public void testTry_catch1() throws Exception { doTest(new TryCatchSurrounder()); }
  public void testTry_catch_finally() throws Exception { doTest(new TryCatchFinallySurrounder()); }
  public void testTry_finally1() throws Exception { doTest(new TryFinallySurrounder()); }
  public void testTry_finallyFormatting() throws Exception { doTest(new TryFinallySurrounder()); }
  public void testWhile1() throws Exception { doTest(new WhileSurrounder()); }
  public void testWith2() throws Exception { doTest(new WithStatementsSurrounder()); }
  public void testFor1() throws Exception { doTest(new ForSurrounder()); }
  public void testIfComments() throws Exception { doTest(new IfSurrounder()); }
  public void testBracesInIf() {
    doTest(new GrBracesSurrounder(), '''\
if (abc)
    pr<caret>int 'abc'
''', '''\
if (abc) {
    print 'abc'
}
''')
  }

  public void testBracesInWhile() {
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
