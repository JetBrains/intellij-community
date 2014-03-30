package com.siyeh.ig.fixes.abstraction;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.abstraction.TypeMayBeWeakenedInspection;

/**
 * @author Bas Leijdekkers
 */
public class TypeMayBeWeakenedFixTest extends IGQuickFixesTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new TypeMayBeWeakenedInspection());
    myRelativePath = "abstraction/type_may_be_weakened";
  }

  public void testShorten() { doTest(InspectionGadgetsBundle.message("type.may.be.weakened.quickfix", "java.util.Collection")); }

}
