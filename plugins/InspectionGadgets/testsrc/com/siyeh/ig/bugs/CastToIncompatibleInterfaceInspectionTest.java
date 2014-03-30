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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.LocalInspectionTool;
import com.siyeh.ig.LightInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class CastToIncompatibleInterfaceInspectionTest extends LightInspectionTestCase {

  public void testSimple() {
    doTest("class X { " +
           "  I list = (/*Cast to incompatible interface 'I'*/I/**/) new C(); " +
           "}" +
           "interface I {}" +
           "class C {}");
  }

  public void testHashMap() {
    doTest("import java.util.HashMap;" +
           "import java.util.List;" +
           "class X {" +
           "  List l = (/*Cast to incompatible interface 'List'*/List/**/) new HashMap();" +
           "}");
  }

  @Override
  protected LocalInspectionTool getInspection() {
    return new CastToIncompatibleInterfaceInspection();
  }
}
