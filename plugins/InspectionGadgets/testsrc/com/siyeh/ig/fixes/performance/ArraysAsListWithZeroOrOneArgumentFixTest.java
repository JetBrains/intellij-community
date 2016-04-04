/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  public void testOneArgument() {
    doTest(InspectionGadgetsBundle.message("arrays.as.list.with.one.argument.quickfix"),
           "import java.util.*;" +
           "class X {{\n" +
           "  List<Map<String, String>> list = Arrays.<Map<String, String>>/**/asList(new HashMap<String, String>());" +
           "}}",
           "import java.util.*;" +
           "class X {{\n" +
           "  List<Map<String, String>> list = Collections.<Map<String, String>>singletonList(new HashMap<String, String>());" +
           "}}");
  }

  @Override
  protected BaseInspection getInspection() {
    return new ArraysAsListWithZeroOrOneArgumentInspection();
  }
}
