// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.imports;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
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
