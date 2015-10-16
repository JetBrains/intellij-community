/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.siyeh.ig.threading;

import com.intellij.codeInspection.LocalInspectionTool;
import com.siyeh.ig.LightInspectionTestCase;

public class NestedSynchronizedStatementInspectionTest extends LightInspectionTestCase {

  @Override
  protected LocalInspectionTool getInspection() {
    return new NestedSynchronizedStatementInspection();
  }

  public void testClassInitializer() {
    doTest("class C {\n" +
           "  {\n" +
           "    synchronized (C.class) {\n" +
           "      /*Nested 'synchronized' statement*/synchronized/**/ (C.class){\n" +
           "      }\n" +
           "    }\n" +
           "  }\n" +
           "}\n");
  }

  public void testInsideLambda() {
    doTest("class C {\n" +
           "  void m() {\n" +
           "   synchronized (C.class){\n" +
           "     new Thread(() -> {synchronized (C.class) {}});\n" +
           "   }\n" +
           "  }\n" +
           "}\n");
  }
}
