package com.siyeh.ig.controlflow;

import com.siyeh.ig.IGInspectionTestCase;

/**
 * Unit test for PointlessNullCheckInspection.
 *
 * @author Lars Fischer
 * @author Etienne Studer
 * @author Hamlet D'Arcy
 */

public class PointlessNullCheckInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/controlflow/pointless_null_check", new PointlessNullCheckInspection());
  }
}
