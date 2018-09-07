// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.performance;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.performance.SetReplaceableByEnumSetInspection;

public class SetReplaceableByEnumSetFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new SetReplaceableByEnumSetInspection());
    myRelativePath = "performance/set_replaceable_with_enum_set";
    myDefaultHint = InspectionGadgetsBundle.message("set.replaceable.by.enum.set.fix.name");
    myFixture.addClass("package java.util;\n" +
                       "\n" +
                       "public abstract class EnumSet<E extends Enum<E>> extends AbstractSet<E>\n" +
                       "  implements Cloneable, java.io.Serializable\n" +
                       "{\n" +
                       "  EnumSet(Class<E>elementType, Enum<?>[] universe) {\n" +
                       "    \n" +
                       "  }\n" +
                       "}");
    myFixture.addClass("import java.util.AbstractSet;\n" +
                       "import java.util.Set;\n" +
                       "\n" +
                       "public class HashSet<E>\n" +
                       "  extends AbstractSet<E>\n" +
                       "  implements Set<E>, Cloneable, java.io.Serializable {\n" +
                       "  public HashSet() {\n" +
                       "  }\n" +
                       "}");

  }


  public void testSimple() {
    doTest();
  }


}