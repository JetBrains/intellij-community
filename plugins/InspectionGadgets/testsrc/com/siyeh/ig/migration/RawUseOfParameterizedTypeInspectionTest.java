package com.siyeh.ig.migration;

import com.siyeh.ig.IGInspectionTestCase;

public class RawUseOfParameterizedTypeInspectionTest extends IGInspectionTestCase {

  public void test() {
    final RawUseOfParameterizedTypeInspection tool = new RawUseOfParameterizedTypeInspection();
    tool.ignoreObjectConstruction = false;
    tool.ignoreUncompilable = true;
    tool.ignoreParametersOfOverridingMethods = true;
    tool.ignoreTypeCasts = true;
    doTest("com/siyeh/igtest/migration/raw_use_of_parameterized_type", tool);
  }
}