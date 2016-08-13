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
package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("UnnecessaryQualifierForThis")
public class UnnecessaryQualifierForThisInspectionLightTest extends LightInspectionTestCase {

  public void testFinalWithoutInnerClass() throws Exception {
    doTest("class Base {\n" +
           "    void foo() {\n" +
           "    }\n" +
           "}\n" +
           "class Impl extends Base {\n" +
           "    void foo() {\n" +
           "        /*Qualifier 'Impl' on 'super' is unnecessary in this context*/Impl/**/.super.foo();\n" +
           "    }\n" +
           "}");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new UnnecessaryQualifierForThisInspection();
  }
}
