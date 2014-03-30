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
package com.siyeh.ig.visibility;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class MethodOverridesPrivateMethodInspectionTest extends LightInspectionTestCase {

  @Override
  protected InspectionProfileEntry getInspection() {
    return new MethodOverridesPrivateMethodInspection();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package test;" +
      "public class Super implements java.io.Serializable {" +
      "private void readObject(java.io.ObjectInputStream s){}" +
      "private void writeObject(java.io.ObjectOutputStream s){}" +
      "private void other() {}" +
      "}"
    };
  }

  public void testSerializationMethod() {
    doTest("import test.Super;" +
           "class A extends Super {" +
           "  private void readObject(java.io.ObjectInputStream s) {}" +
           "}");
  }

  public void testSimple() {
    doTest("import test.Super;" +
           "class B extends Super {" +
           "  private void /*Method 'other()' overrides a private method of a superclass*/other/**/() {}" +
           "}");
  }
}
