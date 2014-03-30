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
package com.siyeh.ig.threading;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class DoubleCheckedLockingInspectionTest extends LightInspectionTestCase {

  public void testSimple() {
    doTest("class A {" +
           "    private  boolean initialized;\n" +
           "    private void initialize() {\n" +
           "        /*Double-checked locking*/if/**/ (initialized == false) {\n" +
           "            synchronized (this) {\n" +
           "                if (initialized == false) {\n" +
           "                    initialized = true;\n" +
           "                }\n" +
           "            }\n" +
           "        }\n" +
           "    }\n" +
           "}");
  }

  public void testVolatile() {
    doTest("class X {" +
           "    private volatile boolean initialized;\n" +
           "    private void initialize() {\n" +
           "        if (initialized == false) {\n" +
           "            synchronized (this) {\n" +
           "                if (initialized == false) {\n" +
           "                    initialized = true;\n" +
           "                }\n" +
           "            }\n" +
           "        }\n" +
           "    }\n" +
           "}");
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    final DoubleCheckedLockingInspection inspection = new DoubleCheckedLockingInspection();
    inspection.ignoreOnVolatileVariables = true;
    return inspection;
  }
}
