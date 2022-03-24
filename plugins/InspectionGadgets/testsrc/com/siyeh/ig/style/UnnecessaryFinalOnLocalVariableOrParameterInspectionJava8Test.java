// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class UnnecessaryFinalOnLocalVariableOrParameterInspectionJava8Test extends LightJavaInspectionTestCase {

  public void testFinalWithoutInnerClass() {
    doTest("class Issue {\n" +
           "    public static void main(String[] args) {\n" +
           "        /*Unnecessary 'final' on variable 's'*/final/**/ int s;\n" +
           "        if (args.length == 0) {\n" +
           "            s = 1;\n" +
           "        } else {\n" +
           "            s = 2;\n" +
           "        }\n" +
           "        new Runnable() {\n" +
           "            @Override\n" +
           "            public void run() {\n" +
           "                System.out.println(s);\n" +
           "            }\n" +
           "        };" +
           "        System.out.println(s);\n" +
           "    }\n" +
           "}");
  }

  public void testInterfaceMethods() {
    final UnnecessaryFinalOnLocalVariableOrParameterInspection inspection = new UnnecessaryFinalOnLocalVariableOrParameterInspection();
    inspection.onlyWarnOnAbstractMethods = true;
    myFixture.enableInspections(inspection);
    doTest("interface X {" +
           "  default void m(final String s) {}" +
           "  static void n(final String s) {}" +
           "  void o(/*Unnecessary 'final' on parameter 's'*/final/**/ String s);" +
           "}");
  }
  
  public void testTryWithResources() {
    final UnnecessaryFinalOnLocalVariableOrParameterInspection inspection = new UnnecessaryFinalOnLocalVariableOrParameterInspection();
    inspection.reportLocalVariables = false;
    myFixture.enableInspections(inspection);
    doTest("import java.io.*;\n" +
           "import java.util.*;\n" +
           "\n" +
           "class FinalTest {\n" +
           "  public List<String> foobar(/*Unnecessary 'final' on parameter 'shouldBeNonFinal'*/final/**/ String shouldBeNonFinal) throws IOException {\n" +
           "    List<String> finalVar = new ArrayList<>();\n" +
           "\n" +
           "    try (final BufferedReader reader = new BufferedReader(new FileReader(\"\"))) {\n" +
           "      for (String nonFinalVar = reader.readLine(); nonFinalVar != null; nonFinalVar = reader.readLine()) {\n" +
           "        finalVar.add(nonFinalVar);\n" +
           "      }\n" +
           "    }\n" +
           "\n" +
           "    for (/*Unnecessary 'final' on parameter 's'*/final/**/ String s : finalVar) {\n" +
           "\n" +
           "    }\n" +
           "    for (final Iterator<String> it = finalVar.iterator(); it.hasNext(); ) {\n" +
           "      if (it.next() == null) {\n" +
           "        System.out.println(\"deleting\");\n" +
           "        it.remove();\n" +
           "      }\n" +
           "    }\n" +
           "    return finalVar;\n" +
           "  }\n" +
           "}");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new UnnecessaryFinalOnLocalVariableOrParameterInspection();
  }
}
