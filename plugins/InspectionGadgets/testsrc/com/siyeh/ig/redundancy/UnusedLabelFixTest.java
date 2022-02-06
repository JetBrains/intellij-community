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
package com.siyeh.ig.redundancy;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.javadoc.UnnecessaryInheritDocInspection;

/**
 * @author Fabrice TIERCELIN
 */
public class UnusedLabelFixTest extends IGQuickFixesTestCase {
  @Override
  protected BaseInspection getInspection() {
    return new UnusedLabelInspection();
  }

  public void testRemoveLabel() {
    doMemberTest(InspectionGadgetsBundle.message("unused.label.remove.quickfix"),
           "  public void myMethod(int count) {\n" +
           "    label/**/: for (int i = 0; i < count; i++) {\n" +
           "      if (i == 3) {\n" +
           "        break;\n" +
           "      }\n" +
           "    }\n" +
           "  }\n",

                 "  public void myMethod(int count) {\n" +
                 "    for (int i = 0; i < count; i++) {\n" +
                 "        if (i == 3) {\n" +
                 "            break;\n" +
                 "        }\n" +
                 "    }\n" +
                 "  }\n"
    );
  }

  public void testDoNotFixUsedLabel() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("unused.label.remove.quickfix"),
                               "class Example {\n" +
                               "  public void myMethod(int count) {\n" +
                               "    label/**/: for (int i = 0; i < count; i++) {\n" +
                               "      for (int j = 0; j < count; j++) {\n" +
                               "        if (i == 3) {\n" +
                               "          break label;\n" +
                               "        }\n" +
                               "      }\n" +
                               "    }\n" +
                               "  }\n" +
                               "}\n"
    );
  }
}
