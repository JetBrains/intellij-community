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
package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.LocalInspectionTool;
import com.siyeh.ig.LightInspectionTestCase;

public class ThrownCaughtLocallyInspectionTest extends LightInspectionTestCase {

  @Override
  protected LocalInspectionTool getInspection() {
    return new ThrowCaughtLocallyInspection();
  }
  
  public void testLambdaOrAnonymous() {
    doTest("class C {\n" +
           "private Object bar() {\n" +
           "        try{\n" +
           "            Runnable runnable = () -> {\n" +
           "                throw new RuntimeException();\n" +
           "            };\n" +
           "            Runnable runnableLambda = new Runnable() {\n" +
           "                @Override\n" +
           "                public void run() {\n" +
           "                    throw new RuntimeException();\n" +
           "                }\n" +
           "            };\n" +
           "        }\n" +
           "        catch (RuntimeException e){\n" +
           "            throw new RuntimeException(e);\n" +
           "        }\n" +
           "        return null;\n" +
           "    }" +
           "}");
  }
}
