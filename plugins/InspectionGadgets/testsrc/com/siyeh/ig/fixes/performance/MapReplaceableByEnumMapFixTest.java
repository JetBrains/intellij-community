// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.performance;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.performance.MapReplaceableByEnumMapInspection;

public class MapReplaceableByEnumMapFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new MapReplaceableByEnumMapInspection());
    myRelativePath = "performance/map_replaceable_with_enum_map";
    myDefaultHint = InspectionGadgetsBundle.message("map.replaceable.by.enum.map.fix.name");
    myFixture.addClass("package java.util;\n" +
                       "\n" +
                       "public class EnumMap<K extends Enum<K>, V> extends AbstractMap<K, V>\n" +
                       "  implements java.io.Serializable, Cloneable\n" +
                       "{\n" +
                       "  public EnumMap(Class<K> keyType) {\n" +
                       "    \n" +
                       "  }\n" +
                       "}");

  }


  public void testSimple() {
    doTest();
  }
  public void testParentheses() {
    doTest();
  }


}