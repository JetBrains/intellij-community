// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.performance;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.performance.DynamicRegexReplaceableByCompiledPatternInspection;

public class DynamicRegexReplaceableByCompiledPatternFixTest extends IGQuickFixesTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new DynamicRegexReplaceableByCompiledPatternInspection());
    myRelativePath = "performance/replace_with_compiled_pattern";
    myDefaultHint = InspectionGadgetsBundle.message("dynamic.regex.replaceable.by.compiled.pattern.quickfix");
    myFixture.addClass("package java.util.regex;" +
                       "public class Pattern {" +
                       "  public static Pattern compile(String regex, int flags) {\n" +
                       "    return null;\n" +
                       "  }" +
                       "  public Matcher matcher(CharSequence input) {" +
                       "    return null;" +
                       "  }" +
                       "}");
    myFixture.addClass("package java.util.regex;" +
                       "public class Matcher {" +
                       "  public String replaceAll(String replacement) {" +
                       "    return \"\";" +
                       "  }" +
                       "  public static String quoteReplacement(String s) {" +
                       "    return s;" +
                       "  }" +
                       "}");
  }

  public void testLiteral() { doTest(); }
  public void testLiteralLiteral() { doTest(); }
}
