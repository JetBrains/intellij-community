package com.siyeh.ig.fixes.abstraction;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.abstraction.TypeMayBeWeakenedInspection;
import com.intellij.util.containers.OrderedSet;
import com.siyeh.ig.IGQuickFixesTestCase;

import java.util.Collections;

/**
 * @author Bas Leijdekkers
 */
public class TypeMayBeWeakenedFixTest extends IGQuickFixesTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    final TypeMayBeWeakenedInspection inspection = new TypeMayBeWeakenedInspection();
    inspection.onlyWeakentoInterface = false;
    inspection.doNotWeakenReturnType = false;
    inspection.myStopClassSet = new OrderedSet<>(Collections.singletonList("com.siyeh.igfixes.abstraction.type_may_be_weakened.Stop"));
    myFixture.enableInspections(inspection);
    myRelativePath = "abstraction/type_may_be_weakened";
  }

  public void testShorten() { doTest(InspectionGadgetsBundle.message("inspection.type.may.be.weakened.quickfix", "java.util.Collection")); }
  public void testLocalClass() { doTest(InspectionGadgetsBundle.message("inspection.type.may.be.weakened.quickfix", "A")); }
  public void testGeneric() { doTest(
    InspectionGadgetsBundle.message("inspection.type.may.be.weakened.quickfix", "com.siyeh.igfixes.abstraction.type_may_be_weakened.C")); }
  public void testStopClass() {
    doTest(InspectionGadgetsBundle.message("inspection.type.may.be.weakened.quickfix", "com.siyeh.igfixes.abstraction.type_may_be_weakened.Stop"));
  }

}
