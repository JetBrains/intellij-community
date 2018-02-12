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
package com.siyeh.ig.abstraction;

import com.intellij.codeInspection.LocalInspectionTool;
import com.siyeh.ig.LightInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class BooleanParameterInspectionTest extends LightInspectionTestCase {

  public void testSimple() {
    doTest("class X {" +
           "  public void /*'public' method 'm()' with 'boolean' parameter*/m/**/(boolean b) {}" +
           "  public void n() {}" +
           "  public void o(int i) {}" +
           "  void p(boolean b) {}" +
           "}");
  }

  public void testConstructor() {
    doTest("class X {" +
           "  public /*'public' constructor 'X()' with 'boolean' parameter*/X/**/(boolean x) {}" +
           "}");
  }

  public void testSetter() {
    doTest("class X {" +
           "  public void setMyProperty(final boolean myProperty) {}" +
           "}");
  }

  @Override
  protected LocalInspectionTool getInspection() {
    return new BooleanParameterInspection();
  }
}
