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
}
