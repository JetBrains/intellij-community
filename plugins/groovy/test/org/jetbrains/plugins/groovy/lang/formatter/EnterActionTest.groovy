/*
 * Copyright 2003-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang.formatter;

import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.List;

/**
 * @author ilyas
 */
public class EnterActionTest extends GroovyFormatterTestCase {

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/enterAction/";
  }


  private void doTest() throws Throwable {
    final List<String> data = TestUtils.readInput(getTestDataPath() + getTestName(true) + ".test");
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, data.get(0));
    doEnter();
    myFixture.checkResult(data.get(1));
  }

  private def doEnter() {
    myFixture.type((char)'\n')
  }

  public void testClos1() throws Throwable { doTest(); }
  public void testClos2() throws Throwable { doTest(); }
  public void testComment1() throws Throwable { doTest(); }
  public void testComment2() throws Throwable { doTest(); }
  public void testComment3() throws Throwable { doTest(); }
  public void testComment4() throws Throwable { doTest(); }
  public void testDef() throws Throwable { doTest(); }
  public void testDef2() throws Throwable { doTest(); }
  public void testGdoc1() throws Throwable { doTest(); }
  public void testGdoc2() throws Throwable { doTest(); }
  public void testGdoc3() throws Throwable { doTest(); }
  public void testGdoc4() throws Throwable { doTest(); }
  public void testGdoc5() throws Throwable { doTest(); }
  public void testGdoc6() throws Throwable { doTest(); }
  public void testGdoc7() throws Throwable { doTest(); }
  public void testGdoc8() throws Throwable { doTest(); }
  public void testGdoc9() throws Throwable { doTest(); }
  public void testGdoc10() throws Throwable { doTest(); }
  public void testGdoc11() throws Throwable { doTest(); }
  public void testBuildGdocPrecededByGNewLine() throws Throwable { doTest(); }
  public void testGRVY_953() throws Throwable { doTest(); }
  public void testGstring1() throws Throwable { doTest(); }
  public void testGstring10() throws Throwable { doTest(); }
  public void testGstring11() throws Throwable { doTest(); }
  public void testGstring12() throws Throwable { doTest(); }
  public void testGstring13() throws Throwable { doTest(); }
  public void testGstring2() throws Throwable { doTest(); }
  public void testGstring3() throws Throwable { doTest(); }
  public void testGstring4() throws Throwable { doTest(); }
  public void testGstring5() throws Throwable { doTest(); }
  public void testGstring6() throws Throwable { doTest(); }
  public void testGstring7() throws Throwable { doTest(); }
  public void testGstring8() throws Throwable { doTest(); }
  public void testGstring9() throws Throwable { doTest(); }
  public void testSpaces1() throws Throwable { doTest(); }
  public void testString1() throws Throwable { doTest(); }
  public void testString2() throws Throwable { doTest(); }
  public void testString3() throws Throwable { doTest(); }
  public void testString4() throws Throwable { doTest(); }
  public void testString5() throws Throwable { doTest(); }
  public void testString6() throws Throwable { doTest(); }

  public void testAfterClosureArrow() throws Throwable {
    myFixture.configureByText "a.groovy", """
def c = { a -><caret> }
"""
    doEnter()
    myFixture.checkResult """
def c = { a ->
  <caret>
}
"""
  }
  public void testAfterClosureArrowWithBody() throws Throwable {
    myFixture.configureByText "a.groovy", """
def c = { a -><caret> zzz }
"""
    doEnter()
    myFixture.checkResult """
def c = { a ->
<caret>  zzz }
"""
  }
  public void testAfterClosureArrowWithBody2() throws Throwable {
    myFixture.configureByText "a.groovy", """
def c = { a -> <caret>zzz }
"""
    doEnter()
    myFixture.checkResult """
def c = { a ->
  <caret>zzz }
""", true
  }

  public void testBeforeClosingClosureBrace() throws Throwable {
    myFixture.configureByText "a.groovy", """
def c = { a ->
  zzz <caret>}
"""
    doEnter()
    myFixture.checkResult """
def c = { a ->
  zzz 
<caret>}
"""
  }
  
  public void testAlmostBeforeClosingClosureBrace() throws Throwable {
    myFixture.configureByText "a.groovy", """
def c = { a ->
  zzz<caret> }
"""
    doEnter()
    myFixture.checkResult """
def c = { a ->
  zzz
<caret>}
"""
  }

}

