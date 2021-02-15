// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.performance;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.performance.ArraysAsListWithZeroOrOneArgumentInspection;

/**
 * @author Bas Leijdekkers
 */
public class ArraysAsListWithZeroOrOneArgumentFixTest extends IGQuickFixesTestCase {

  public void testZeroArguments() {
    doTest(CommonQuickFixBundle.message("fix.replace.with.x", "Collections.emptyList()"),
           "import java.util.*;\n" +
           "class X {{\n" +
           "    Arrays.asList/**/();\n" +
           "}}",
           "import java.util.*;\n" +
           "class X {{\n" +
           "    Collections.emptyList();\n" +
           "}}");
  }

  @SuppressWarnings("RedundantOperationOnEmptyContainer")
  public void testZeroArgumentsWithType() {
    doTest(CommonQuickFixBundle.message("fix.replace.with.x", "Collections.emptyList()"),
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
    doTest(CommonQuickFixBundle.message("fix.replace.with.x", "Collections.singletonList()"),
           "import java.util.*;" +
           "class X {{\n" +
           "  List<Map<String, String>> list = Arrays./**/asList(new HashMap<>());" +
           "}}",
           "import java.util.*;" +
           "class X {{\n" +
           "  List<Map<String, String>> list = Collections.singletonList(new HashMap<>());" +
           "}}");
  }

  @Override
  protected BaseInspection getInspection() {
    return new ArraysAsListWithZeroOrOneArgumentInspection();
  }
}
