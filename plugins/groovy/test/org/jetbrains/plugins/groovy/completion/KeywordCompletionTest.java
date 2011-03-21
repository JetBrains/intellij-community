/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.completion;

import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author ilyas
 */
public class KeywordCompletionTest extends CompletionTestBase {

  public void testBr1() throws Throwable { doTest(); }
  public void testCase_return() throws Throwable { doTest(); }
  public void testClass1() throws Throwable { doTest(); }
  public void testClass2() throws Throwable { doTest(); }
  public void testClass3() throws Throwable { doTest(); }
  public void testClass4() throws Throwable { doTest(); }
  public void testExpr1() throws Throwable { doTest(); }
  public void testExpr2() throws Throwable { doTest(); }
  public void testFile11() throws Throwable { doTest(); }
  public void testFile12() throws Throwable { doTest(); }
  public void testFin() throws Throwable { doTest(); }
  public void testFin2() throws Throwable { doTest(); }
  public void testGRVY1064() throws Throwable { doTest(); }
  public void testGrvy1404() throws Throwable { doTest(); }
  public void testImp1() throws Throwable { doTest(); }
  public void testImp2() throws Throwable { doTest(); }
  public void testImp3() throws Throwable { doTest(); }
  public void testImp4() throws Throwable { doTest(); }
  public void testIns1() throws Throwable { doTest(); }
  public void testIns2() throws Throwable { doTest(); }
  public void testIns3() throws Throwable { doTest(); }
  public void testInt1() throws Throwable { doTest(); }
  public void testLocal1() throws Throwable { doTest(); }
  public void testMod1() throws Throwable { doTest(); }
  public void testMod10() throws Throwable { doTest(); }
  public void testMod11() throws Throwable { doTest(); }
  public void testMod2() throws Throwable { doTest(); }
  public void testMod3() throws Throwable { doTest(); }
  public void testMod4() throws Throwable { doTest(); }
  public void testMod5() throws Throwable { doTest(); }
  public void testMod6() throws Throwable { doTest(); }
  public void testMod7() throws Throwable { doTest(); }
  public void testMod8() throws Throwable { doTest(); }
  public void testMod9() throws Throwable { doTest(); }
  public void testPack1() throws Throwable { doTest(); }
  public void testSt1() throws Throwable { doTest(); }
  public void testSwit1() throws Throwable { doTest(); }
  public void testSwit13() throws Throwable { doTest(); }
  public void testSwit14() throws Throwable { doTest(); }
  public void testSwit2() throws Throwable { doTest(); }
  public void testSwit3() throws Throwable { doTest(); }
  public void testSwit4() throws Throwable { doTest(); }
  public void testSwit5() throws Throwable { doTest(); }
  public void testTag1() throws Throwable { doTest(); }
  public void testTag2() throws Throwable { doTest(); }
  public void testTag3() throws Throwable { doTest(); }
  public void testTag4() throws Throwable { doTest(); }
  public void testTh1() throws Throwable { doTest(); }
  public void testTh2() throws Throwable { doTest(); }
  public void testVar1() throws Throwable { doTest(); }
  public void testVar10() throws Throwable { doTest(); }
  public void testVar13() throws Throwable { doTest(); }
  public void testVar2() throws Throwable { doTest(); }
  public void testVar3() throws Throwable { doTest(); }
  public void testVar4() throws Throwable { doTest(); }
  public void testVar5() throws Throwable { doTest(); }
  public void testVar6() throws Throwable { doTest(); }
  public void testVar7() throws Throwable { doTest(); }
  public void testVar8() throws Throwable { doTest(); }
  public void testWhile55() throws Throwable { doTest(); }


  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/oldCompletion/keyword";
  }

  @Override
  protected boolean addReferenceVariants() {
    return false;
  }

}
