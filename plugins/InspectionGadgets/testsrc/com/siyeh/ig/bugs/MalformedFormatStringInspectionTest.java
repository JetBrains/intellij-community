package com.siyeh.ig.bugs;

import com.siyeh.ig.IGInspectionTestCase;

public class MalformedFormatStringInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    MalformedFormatStringInspection inspection = new MalformedFormatStringInspection();
    inspection.classNames.add("com.siyeh.igtest.bugs.malformed_format_string.MalformedFormatString.SomeOtherLogger");
    inspection.methodNames.add("d");

    doTest("com/siyeh/igtest/bugs/malformed_format_string", inspection);
  }
}