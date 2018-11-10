// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class ObjectsEqualsCanBeSimplifiedInspectionTest extends LightInspectionTestCase {

  public void testObjectsEqualsCanBeSimplified() {
    doTest();
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new ObjectsEqualsCanBeSimplifiedInspection();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @Override
  protected String getBasePath() {
    return "/plugins/InspectionGadgets/test/com/siyeh/igtest/style/simplify_objects_equals";
  }
}