// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.performance;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;

/**
 * @author Bas Leijdekkers
 */
public class MapReplaceableByEnumMapFixTest extends IGQuickFixesTestCase {

  public void testMapReplaceableByEnumMapFix() {
    doTest(InspectionGadgetsBundle.message("map.replaceable.by.enum.map.fix.name"));
  }

  @Override
  protected String getRelativePath() {
    return "performance/map_replaceable_by_enum_map";
  }

  @Override
  protected BaseInspection getInspection() {
    return new MapReplaceableByEnumMapInspection();
  }
}