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
package com.siyeh.ig.visibility;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class LocalVariableHidingMemberVariableInspectionTest extends LightInspectionTestCase {

  public void testLocalVariableHidingMemberVariable() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final LocalVariableHidingMemberVariableInspection inspection = new LocalVariableHidingMemberVariableInspection();
    inspection.m_ignoreStaticMethods = true;
    return inspection;
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package com.siyeh.igtest.visibility2;\n" +
      "public class DifferentPackageClass\n" +
      "{\n" +
      "    int fooBar;\n" +
      "    protected int fooBar2;\n" +
      "}\n"
    };
  }
}