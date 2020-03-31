/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
@SuppressWarnings("InterfaceMethodClashesWithObject")
public class InterfaceMethodClashesWithObjectInspectionTest extends LightJavaInspectionTestCase {

  public void testBadClone() {
    doTest("interface BadClone {" +
           "  int /*'clone()' clashes with method in 'java.lang.Object'*/clone/**/();" +
           "}");
  }

  public void testBadFinalize() {
    doTest("interface BadFinalize {" +
           "  Object /*'finalize()' clashes with method in 'java.lang.Object'*/finalize/**/();" +
           "}");
  }

  public void testGoodClone() {
    doTest("interface GoodClone {" +
           "  GoodClone clone() throws CloneNotSupportedException;" +
           "}");
  }

  public void testAnnotated() {
    doTest("interface A {" +
           "    @Deprecated" +
           "    void finalize();" +
           "}" +
           "class B implements A {" +
           "    public @Deprecated void finalize() {}" +
           "}");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new InterfaceMethodClashesWithObjectInspection();
  }
}