package com.siyeh.ig.migration;

import com.siyeh.ig.IGInspectionTestCase;

public class RawUseOfParameterizedTypeInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    final RawUseOfParameterizedTypeInspection tool = new RawUseOfParameterizedTypeInspection();
    tool.ignoreUncompilable = true;
    tool.ignoreParametersOfOverridingMethods = true;
    doTest("com/siyeh/igtest/migration/raw_use_of_parameterized_type", tool);
  }
}