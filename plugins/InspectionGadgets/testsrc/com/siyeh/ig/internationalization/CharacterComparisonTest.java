package com.siyeh.ig.internationalization;

import com.siyeh.ig.IGInspectionTestCase;

public class CharacterComparisonTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/internationalization/character_comparison", new CharacterComparisonInspection());
  }
}