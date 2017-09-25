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
package com.siyeh.ig.maturity;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class ThrowablePrintStackTraceInspectionTest extends LightInspectionTestCase {

  public void testSimple() {
    doStatementTest("new Throwable()./*Call to 'printStackTrace()' should probably be replaced with more robust logging*/printStackTrace/**/();");
  }

  public void testInTestCode() {
    addEnvironmentClass("package org.junit;" +
                        "@Retention(RetentionPolicy.RUNTIME) " +
                        "@Target({ElementType.METHOD}) " +
                        "public @interface Test {}");
    doMemberTest("@org.junit.Test public void testSomething() {" +
                 "  new RuntimeException()./*Call to 'printStackTrace()' should probably be replaced with more robust logging*/printStackTrace/**/();" +
                 "}");
  }

  public void testPrintStacktraceOverridden() {
    addEnvironmentClass("public class FException extends Exception {\n" +
                        "    @Override\n" +
                        "    public void printStackTrace() {\n" +
                        "        super.printStackTrace();\n" +
                        "    }\n" +
                        "}\n");
    doStatementTest("new FException()./*Call to 'printStackTrace()' should probably be replaced with more robust logging*/printStackTrace/**/();");
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new ThrowablePrintStackTraceInspection();
  }
}
