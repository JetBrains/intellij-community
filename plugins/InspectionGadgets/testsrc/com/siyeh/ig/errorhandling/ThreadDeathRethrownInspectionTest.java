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
package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;

public class ThreadDeathRethrownInspectionTest extends LightJavaInspectionTestCase {

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {"package java.lang;" +
                         "public class ThreadDeath extends Error {}"};
  }

  public void testSimple() {
    doStatementTest("try {" +
                    "  System.out.println(\"foo\");" +
                    "} catch (ThreadDeath /*ThreadDeath 'e' not rethrown*/e/**/) {" +
                    "  e.printStackTrace();" +
                    "}");
  }

  public void testNoWarn() {
    doStatementTest("try {" +
                    "  System.out.println(\"foo\");" +
                    "} catch (ThreadDeath e) {" +
                    "  e.printStackTrace();" +
                    "  throw e;" +
                    "}");
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new ThreadDeathRethrownInspection();
  }
}
