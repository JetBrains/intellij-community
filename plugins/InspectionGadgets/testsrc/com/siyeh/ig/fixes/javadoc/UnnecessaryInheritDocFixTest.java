/*
 * Copyright 2000-2022 JetBrains s.r.o.
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
package com.siyeh.ig.fixes.javadoc;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.imports.JavaLangImportInspection;
import com.siyeh.ig.javadoc.UnnecessaryInheritDocInspection;

/**
 * @author Fabrice TIERCELIN
 */
public class UnnecessaryInheritDocFixTest extends IGQuickFixesTestCase {
  @Override
  protected void tuneFixture(final JavaModuleFixtureBuilder builder) throws Exception {
    builder.setLanguageLevel(LanguageLevel.JDK_1_9);
  }

  @Override
  protected BaseInspection getInspection() {
    return new UnnecessaryInheritDocInspection();
  }

  public void testRemoveJavaLangImport() {
    doTest(InspectionGadgetsBundle.message("unnecessary.inherit.doc.quickfix"),
           "  class Example implements Comparable<Example> {\n" +
           "    /**\n" +
           "     * {@inheritDoc}<caret>\n" +
           "     */\n" +
           "    @Override\n" +
           "    public int compareTo(Example o) {\n" +
           "      return 0;\n" +
           "    }\n" +
           "  }\n",

           "  class Example implements Comparable<Example> {\n" +
           "    @Override\n" +
           "    public int compareTo(Example o) {\n" +
           "      return 0;\n" +
           "    }\n" +
           "  }\n"
    );
  }

  public void testDoNotFixCompletedDoc() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("unnecessary.inherit.doc.quickfix"),
                               "  class Example implements Comparable<Example> {\n" +
                               "    /**\n" +
                               "     * {@inheritDoc}<caret>\n" +
                               "     * This implementation always returns zero.\n" +
                               "     */\n" +
                               "    @Override\n" +
                               "    public int compareTo(Example o) {\n" +
                               "      return 0;\n" +
                               "    }\n" +
                               "  }\n"
    );
  }
}
