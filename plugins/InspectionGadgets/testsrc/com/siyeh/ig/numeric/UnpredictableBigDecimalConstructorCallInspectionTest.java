/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.siyeh.ig.numeric;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;

public class UnpredictableBigDecimalConstructorCallInspectionTest extends LightJavaInspectionTestCase {

  @Override
  protected InspectionProfileEntry getInspection() {
    return new UnpredictableBigDecimalConstructorCallInspection();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package java.math;" +
      "public class BigDecimal {" +
      "  public BigDecimal(double d) {}" +
      "  public BigDecimal(int i) {}" +
      "}"
    };
  }

  public void testNotMathBigDecimal() {
    doTest("class X {" +
           "  void foo() {" +
           "    new BigDecimal(.1);" +
           "  }" +
           "  class BigDecimal {" +
           "    BigDecimal(double d) {}" +
           "  }" +
           "}");
  }

  public void testSimple() {
    doTest("import java.math.*;" +
           "class X {" +
           "  void foo() {" +
           "    new /*Unpredictable 'new BigDecimal()' call*/BigDecimal/**/(.1);" +
           "    new BigDecimal(1);" +
           "  }" +
           "}");
  }
}