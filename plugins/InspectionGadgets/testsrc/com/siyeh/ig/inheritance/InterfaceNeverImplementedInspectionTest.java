// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.inheritance;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InterfaceNeverImplementedInspectionTest extends LightInspectionTestCase {

  public void testInterfaceNeverImplemented() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final InterfaceNeverImplementedInspection inspection = new InterfaceNeverImplementedInspection();
    inspection.ignorableAnnotations.add("com.intellij.test.Ignore");
    return inspection;
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package com.intellij.test;" +
      "public @interface Ignore {}"
    };
  }
}
