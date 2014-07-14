/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.siyeh.ipp.interfacetoclass;

import com.intellij.refactoring.BaseRefactoringProcessor;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

public class ConvertInterfaceToClassTest extends IPPTestCase {
  public void testBasic() { doTest(); }
  public void testExtensionMethods() { doTest(); }
  public void testInnerInterface() { doTest(); }
  public void testStaticMethods() { doTest(); }
  public void testInterfaceExtendsClass() { doTest(); }
  public void testFunctionalExpressions() {
    try {
      doTest();
      fail("Conflict not detected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Functional expression in Test will not compile after converting class <b><code>FunctionalExpressions</code></b> to a class", e.getMessage());
    }
  }

  public void testFunctionalInterface() throws Exception {
    assertIntentionNotAvailable();
  }

  @Override
  protected String getRelativePath() {
    return "interfaceToClass";
  }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("convert.interface.to.class.intention.name");
  }
}
