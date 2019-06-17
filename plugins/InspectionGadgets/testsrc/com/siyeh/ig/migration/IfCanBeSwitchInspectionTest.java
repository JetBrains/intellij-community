// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.migration;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class IfCanBeSwitchInspectionTest extends LightJavaInspectionTestCase {

  public void testIfCanBeSwitch() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final IfCanBeSwitchInspection inspection = new IfCanBeSwitchInspection();
    inspection.suggestIntSwitches = true;
    inspection.suggestEnumSwitches = true;
    inspection.minimumBranches = 2;
    inspection.setOnlySuggestNullSafe(true);
    return inspection;
  }

  @Override
  protected String getBasePath() {
    return "/plugins/InspectionGadgets/test/com/siyeh/igtest/migration/if_switch";
  }
}
