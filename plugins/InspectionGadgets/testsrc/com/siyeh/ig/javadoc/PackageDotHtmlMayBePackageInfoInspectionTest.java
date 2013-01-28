package com.siyeh.ig.javadoc;

import com.siyeh.ig.IGInspectionTestCase;

public class PackageDotHtmlMayBePackageInfoInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/javadoc/package_dot_html_may_be_package_info", new PackageDotHtmlMayBePackageInfoInspection());
  }
}