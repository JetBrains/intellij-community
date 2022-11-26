// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class UnnecessaryFinalOnLocalVariableOrParameterInspectionJava8Test extends LightJavaInspectionTestCase {

  public void testFinalWithoutInnerClass() {
    doTest("""
             class Issue {
                 public static void main(String[] args) {
                     /*Unnecessary 'final' on variable 's'*/final/**/ int s;
                     if (args.length == 0) {
                         s = 1;
                     } else {
                         s = 2;
                     }
                     new Runnable() {
                         @Override
                         public void run() {
                             System.out.println(s);
                         }
                     };        System.out.println(s);
                 }
             }""");
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
    doTest("""
             import java.io.*;
             import java.util.*;

             class FinalTest {
               public List<String> foobar(/*Unnecessary 'final' on parameter 'shouldBeNonFinal'*/final/**/ String shouldBeNonFinal) throws IOException {
                 List<String> finalVar = new ArrayList<>();

                 try (final BufferedReader reader = new BufferedReader(new FileReader(""))) {
                   for (String nonFinalVar = reader.readLine(); nonFinalVar != null; nonFinalVar = reader.readLine()) {
                     finalVar.add(nonFinalVar);
                   }
                 }

                 for (/*Unnecessary 'final' on parameter 's'*/final/**/ String s : finalVar) {

                 }
                 for (final Iterator<String> it = finalVar.iterator(); it.hasNext(); ) {
                   if (it.next() == null) {
                     System.out.println("deleting");
                     it.remove();
                   }
                 }
                 return finalVar;
               }
             }""");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new UnnecessaryFinalOnLocalVariableOrParameterInspection();
  }
}
