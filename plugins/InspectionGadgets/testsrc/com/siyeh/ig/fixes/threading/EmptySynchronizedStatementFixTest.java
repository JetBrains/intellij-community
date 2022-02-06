/*
 * Copyright 2000-2022 JetBrains s.r.o.
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
package com.siyeh.ig.fixes.threading;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.controlflow.UnnecessaryBreakInspection;
import com.siyeh.ig.threading.EmptySynchronizedStatementInspection;

/**
 * @author Fabrice TIERCELIN
 */
public class EmptySynchronizedStatementFixTest extends IGQuickFixesTestCase {
  @Override
  protected BaseInspection getInspection() {
    return new EmptySynchronizedStatementInspection();
  }

  public void testRemoveSynchronizedStatement() {
    doMemberTest(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "synchronized"),
                 "  public void printName(String lock) {\n" +
                 "    synchronized/**/(lock) {}\n" +
                 "}\n",
                 "  public void printName(String lock) {\n" +
                 "}\n"
    );
  }

  public void testDoNotFixUsedSynchronizedStatement() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "synchronized"),
                               "class X {\n" +
                               "  public void printName(String lock) {\n" +
                               "    synchronized(lock) {\n" +
                               "      System.out.println(lock);\n" +
                               "    }\n" +
                               "  }\n" +
                               "}\n");
  }
}
