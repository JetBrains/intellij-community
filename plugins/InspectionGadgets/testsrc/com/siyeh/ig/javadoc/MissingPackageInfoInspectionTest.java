/**
 * (c) 2013 Desert Island BV
 * created: 27 09 2013
 */
package com.siyeh.ig.javadoc;

import com.siyeh.ig.IGInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class MissingPackageInfoInspectionTest extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/javadoc/missing_package_info", new MissingPackageInfoInspection());
  }
}
