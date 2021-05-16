// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.performance;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.performance.ArraysAsListWithZeroOrOneArgumentInspection;

/**
 * @author Bas Leijdekkers
 */
public class ArraysAsListWithZeroOrOneArgumentFixJava9Test extends IGQuickFixesTestCase {

  public void testZeroArguments() {
    doTest(CommonQuickFixBundle.message("fix.replace.with.x", "List.of()"),
           "import java.util.*;\n" +
           "class X {{\n" +
           "    Arrays.asList/**/();\n" +
           "}}",
           "import java.util.*;\n" +
           "class X {{\n" +
           "    List.of();\n" +
           "}}");
  }

  @SuppressWarnings("RedundantOperationOnEmptyContainer")
  public void testZeroArgumentsWithType() {
    doTest(CommonQuickFixBundle.message("fix.replace.with.x", "List.of()"),
           "import java.util.*;\n" +
           "class X {{\n" +
           "    Spliterator<String> it = Arrays.<String>/**/asList().spliterator();\n" +
           "}}",
           "import java.util.*;\n" +
           "class X {{\n" +
           "    Spliterator<String> it = List.<String>of().spliterator();\n" +
           "}}");
  }

  public void testOneArgument() {
    doTest(CommonQuickFixBundle.message("fix.replace.with.x", "List.of()"),
           "import java.util.*;" +
           "class X {{\n" +
           "  List<Map<String, String>> list = Arrays./**/asList(new HashMap<>());" +
           "}}",
           "import java.util.*;" +
           "class X {{\n" +
           "  List<Map<String, String>> list = List.of(new HashMap<>());" +
           "}}");
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder builder) throws Exception {
    builder.setLanguageLevel(LanguageLevel.JDK_1_9);
  }

  @Override
  protected BaseInspection getInspection() {
    return new ArraysAsListWithZeroOrOneArgumentInspection();
  }
}
