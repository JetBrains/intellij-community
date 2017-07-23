package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class MapReplaceableByEnumMapInspectionTest extends LightInspectionTestCase {

  public void testMapReplaceableByEnumMap() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new MapReplaceableByEnumMapInspection();
  }
}