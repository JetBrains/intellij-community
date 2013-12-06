package com.siyeh.ig.fixes.classlayout;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.classlayout.ClassInitializerInspection;

/**
 * @author Bas Leijdekkers
 */
public class ClassInitializerFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new ClassInitializerInspection());
    myRelativePath = "classlayout/class_initializer";
    myDefaultHint = InspectionGadgetsBundle.message("class.initializer.move.code.to.constructor.quickfix");
  }

  public void testNoConstructor() { doTest(); }
  public void testChainedConstructor() { doTest(); }
}
