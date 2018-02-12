package com.siyeh.ig.imports;

import com.siyeh.ig.IGInspectionTestCase;

public class StaticImportInspectionTest extends IGInspectionTestCase {

  public void test() {
    final StaticImportInspection tool = new StaticImportInspection();
    tool.allowedClasses.add("java.util.Map");
    doTest("com/siyeh/igtest/imports/static_import", tool);
  }

  public void testMethodAllowed() {
    final StaticImportInspection tool = new StaticImportInspection();
    tool.ignoreSingeMethodImports = true;
    doTest("com/siyeh/igtest/imports/static_import_method_allowed", tool);
  }
}