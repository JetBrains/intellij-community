package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class MapReplaceableByEnumMapInspectionTest extends LightJavaInspectionTestCase {

  public void testMapReplaceableByEnumMap() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new MapReplaceableByEnumMapInspection();
  }
}