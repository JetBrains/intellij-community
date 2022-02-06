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
package com.siyeh.ig.fixes.imports;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.controlflow.UnnecessaryLabelOnContinueStatementInspection;
import com.siyeh.ig.imports.JavaLangImportInspection;

/**
 * @author Fabrice TIERCELIN
 */
public class JavaLangImportFixTest extends IGQuickFixesTestCase {
  @Override
  protected BaseInspection getInspection() {
    return new JavaLangImportInspection();
  }

  public void testRemoveJavaLangImport() {
    doTest(InspectionGadgetsBundle.message("delete.import.quickfix"),
           "import/**/ java.lang.String;\n" +
           "\n" +
           "class X {\n" +
           "  public void printName(String[] names) {\n" +
           "  }\n" +
           "}\n",
           "class X {\n" +
           "  public void printName(String[] names) {\n" +
           "  }\n" +
           "}\n"
    );
  }

  public void testRemoveJavaLangImportOnDemand() {
    doTest(InspectionGadgetsBundle.message("delete.import.quickfix"),
           "import/**/ java.lang.*;\n" +
           "\n" +
           "class X {\n" +
           "  public void printName(String[] names) {\n" +
           "  }\n" +
           "}\n",
           "class X {\n" +
           "  public void printName(String[] names) {\n" +
           "  }\n" +
           "}\n"
    );
  }

  public void testRemoveJavaLangImportAmongOther() {
    doTest(InspectionGadgetsBundle.message("delete.import.quickfix"),
           "import/**/ java.lang.String;\n" +
           "import java.util.Date;\n" +
           "\n" +
           "class X {\n" +
           "  public String printDate(Date date) {\n" +
           "    return date.toString();\n" +
           "  }\n" +
           "}\n",
           "import java.util.Date;\n" +
           "\n" +
           "class X {\n" +
           "  public String printDate(Date date) {\n" +
           "    return date.toString();\n" +
           "  }\n" +
           "}\n"
    );
  }
}
