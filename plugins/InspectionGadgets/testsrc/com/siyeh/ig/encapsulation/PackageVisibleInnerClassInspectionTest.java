package com.siyeh.ig.encapsulation;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class PackageVisibleInnerClassInspectionTest extends LightJavaInspectionTestCase {
  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final PackageVisibleInnerClassInspection tool = new PackageVisibleInnerClassInspection();
    tool.ignoreEnums = true;
    tool.ignoreInterfaces = true;
    return tool;
  }

  public void testPackageVisibleInnerClassInspection() {
    doTest();
  }
}
