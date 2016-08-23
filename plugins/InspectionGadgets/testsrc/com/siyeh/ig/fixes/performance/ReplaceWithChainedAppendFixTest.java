/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.siyeh.ig.performance.StringConcatenationInsideStringBufferAppendInspection;

/**
 * @author Bas Leijdekkers
 */
public class ReplaceWithChainedAppendFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new StringConcatenationInsideStringBufferAppendInspection());
    myRelativePath = "performance/concatenation_inside_append";
    myDefaultHint = InspectionGadgetsBundle.message("string.concatenation.inside.string.buffer.append.replace.quickfix");
  }

  public void testUnresolvedMethod() { doTest(); }

  public void testPrintWriterAppend() {
    myFixture.addClass("package java.lang;" +
                       "public interface Appendable {}");
    myFixture.addClass("package java.io;" +
                       "public class PrintWriter extends java.lang.Appendable {" +
                       "@Override" +
                       "public PrintWriter append(CharSequence csq) throws IOException {" +
                       " return null;" +
                       "}}");
    doTest();
  }

}
