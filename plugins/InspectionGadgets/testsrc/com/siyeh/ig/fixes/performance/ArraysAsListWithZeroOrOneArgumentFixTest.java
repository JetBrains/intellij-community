// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.performance;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.performance.ArraysAsListWithZeroOrOneArgumentInspection;

/**
 * @author Bas Leijdekkers
 */
public class ArraysAsListWithZeroOrOneArgumentFixTest extends IGQuickFixesTestCase {

  public void testZeroArguments() {
    doTest(InspectionGadgetsBundle.message("arrays.as.list.with.zero.arguments.quickfix"),
           "import java.util.*;\n" +
           "class X {{\n" +
           "    Arrays.asList/**/();\n" +
           "}}",
           "import java.util.*;\n" +
           "class X {{\n" +
           "    Collections.emptyList();\n" +
           "}}");
  }

  public void testZeroArgumentsWithType() {
    doTest(InspectionGadgetsBundle.message("arrays.as.list.with.zero.arguments.quickfix"),
           "import java.util.*;\n" +
           "class X {{\n" +
           "    Spliterator<String> it = Arrays.<String>/**/asList().spliterator();\n" +
           "}}",
           "import java.util.*;\n" +
           "class X {{\n" +
           "    Spliterator<String> it = Collections.<String>emptyList().spliterator();\n" +
           "}}");
  }

  public void testOneArgument() {
    doTest(InspectionGadgetsBundle.message("arrays.as.list.with.one.argument.quickfix"),
           "import java.util.*;" +
           "class X {{\n" +
           "  List<Map<String, String>> list = Arrays./**/asList(new HashMap<String, String>());" +
           "}}",
           "import java.util.*;" +
           "class X {{\n" +
           "  List<Map<String, String>> list = Collections.singletonList(new HashMap<String, String>());" +
           "}}");
  }

  @Override
  protected BaseInspection getInspection() {
    return new ArraysAsListWithZeroOrOneArgumentInspection();
  }
}
