/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

public class UnnecessaryFinalOnLocalVariableOrParameterInspectionJava8Test extends LightInspectionTestCase {

  public void testFinalWithoutInnerClass() {
    doTest("class Issue {\n" +
           "    public static void main(String[] args) {\n" +
           "        /*Unnecessary 'final' on variable 's'*/final/**/ Integer s;\n" +
           "        if (args.length == 0) {\n" +
           "            s = 1;\n" +
           "        } else {\n" +
           "            s = 2;\n" +
           "        }\n" +
           "        new Runnable() {\n" +
           "            @Override\n" +
           "            public void run() {\n" +
           "                System.out.println(s);\n" +
           "            }\n" +
           "        };" +
           "        System.out.println(s);\n" +
           "    }\n" +
           "}");
  }

  public void testInterfaceMethods() {
    final UnnecessaryFinalOnLocalVariableOrParameterInspection inspection = new UnnecessaryFinalOnLocalVariableOrParameterInspection();
    inspection.onlyWarnOnAbstractMethods = true;
    myFixture.enableInspections(inspection);
    doTest("interface X {" +
           "  default void m(final String s) {}" +
           "  static void n(final String s) {}" +
           "  void o(/*Unnecessary 'final' on parameter 's'*/final/**/ String s);" +
           "}");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new UnnecessaryFinalOnLocalVariableOrParameterInspection();
  }
}
