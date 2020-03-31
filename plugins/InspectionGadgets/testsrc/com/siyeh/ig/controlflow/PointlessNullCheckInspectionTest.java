package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * Unit test for PointlessNullCheckInspection.
 *
 * @author Lars Fischer
 * @author Etienne Studer
 * @author Hamlet D'Arcy
 */
public class PointlessNullCheckInspectionTest extends LightJavaInspectionTestCase {

  public void testPointlessNullCheck() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new PointlessNullCheckInspection();
  }
}
