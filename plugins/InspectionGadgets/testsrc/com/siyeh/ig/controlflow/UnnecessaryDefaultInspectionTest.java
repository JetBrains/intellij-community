// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class UnnecessaryDefaultInspectionTest extends LightJavaInspectionTestCase {

  public void testUnnecessaryDefault() {
    doTest();
  }

  public void testSwitchExpression() {
    doTest("""
             class X {
               boolean x(E e) {
                 return switch (e) {
                   case A, B -> true;
                   /*'default' branch is unnecessary*//*_*/default/**/ -> false;
                 };
               }
             }""");
    checkQuickFix("Remove 'default' branch",
                  """
                    class X {
                      boolean x(E e) {
                        return switch (e) {
                          case A, B -> true;
                        };
                      }
                    }""");
  }

  public void testSwitchFallthrough() {
    doTest("""
             class X {
               void x(E e) {
                 switch (e) {
                   case A,B:
                       System.out.println(e);
                   /*'default' branch is unnecessary*/default/*_*//**/:
                       System.out.println();
                 }
               }
             }
             """);
    checkQuickFix("Remove 'default' branch",
                  """
                    class X {
                      void x(E e) {
                        switch (e) {
                          case A,B:
                              System.out.println(e);
                              System.out.println();
                        }
                      }
                    }
                    """);
  }

  public void testDeclarationInBranch() {
    doTest("class X {" +
           "  void x(E e) {" +
           "    switch (e) {" +
           "      /*'default' branch is unnecessary*/default/*_*//**/:" +
           "        int x = 1;" +
           "        System.out.println(x);" +
           "      case A,B:" +
           "        x = 2;" +
           "        System.out.println(x);" +
           "      }" +
           "   }" +
           "}");
    checkQuickFix("Remove 'default' branch",
                  """
                    class X {  void x(E e) {    switch (e) {
                        case A,B:
                            int x;
                            x = 2;        System.out.println(x);      }   }}""");
  }

  public void testCaseDefaultInEnumSwitch() {
    doTest("class X {" +
           "  void x(E e) {" +
           "    switch (e) {" +
           "      case A, B:" +
           "        break;" +
           "      case /*'default' branch is unnecessary*/default/*_*//**/:" +
           "        break;" +
           "    }" +
           "  }" +
           "}");
    checkQuickFix("Remove 'default' branch",
                  "class X {" +
                  "  void x(E e) {" +
                  "    switch (e) {" +
                  "      case A, B:" +
                  "        break;\n" +
                  "}" +
                  "  }" +
                  "}");
  }

  public void testCaseDefaultWithEnumElements() {
    doTest("class X {" +
           "  void x(E e) {" +
           "    switch (e) {" +
           "      case A, /*'default' branch is unnecessary*/default/*_*//**/, B:" +
           "        break;" +
           "    }" +
           "  }" +
           "}");
    checkQuickFix("Remove 'default' branch",
                  "class X {" +
                  "  void x(E e) {" +
                  "    switch (e) {" +
                  "      case A, B:" +
                  "        break;" +
                  "    }" +
                  "  }" +
                  "}");
  }

  public void testCaseDefaultInSealedSwitch() {
    doTest("class X {" +
           "  void x(I i) {" +
           "    switch (i) {" +
           "      case (I ii && false):" +
           "        break;" +
           "      case C1 c1:" +
           "        break;" +
           "      case C2 c1:" +
           "        break;" +
           "      case /*'default' branch is unnecessary*/default/*_*//**/:" +
           "        break;" +
           "    }" +
           "  }" +
           "}");
    checkQuickFix("Remove 'default' branch",
                  "class X {" +
                  "  void x(I i) {" +
                  "    switch (i) {" +
                  "      case (I ii && false):" +
                  "        break;" +
                  "      case C1 c1:" +
                  "        break;" +
                  "      case C2 c1:" +
                  "        break;\n" +
                  "}" +
                  "  }" +
                  "}");
  }

  public void testCaseDefaultWithPattern() {
    doTest("class X {" +
           "  void x(I i) {" +
           "    switch (i) {" +
           "      case /*'default' branch is unnecessary*/default/*_*//**/, <error descr=\"Illegal fall-through to a pattern\">(I ii && false)</error>:" +
           "        break;" +
           "      case C1 c1:" +
           "        break;" +
           "      case C2 c2:" +
           "        break;" +
           "    }" +
           "  }" +
           "}");
    checkQuickFix("Remove 'default' branch",
                  "class X {" +
                  "  void x(I i) {" +
                  "    switch (i) {" +
                  "      case (I ii && false):" +
                  "        break;" +
                  "      case C1 c1:" +
                  "        break;" +
                  "      case C2 c2:" +
                  "        break;" +
                  "    }" +
                  "  }" +
                  "}");
  }

  public void testDefaultInParameterizedSealedHierarchy() {
    doTest("class X {" +
           "  void x(J<Integer> j) {" +
           "    switch (j) {" +
           "      case D2 d2 -> {}" +
           "      case default -> {}" +
           "    }" +
           "  }" +
           "}");
  }

  public void testDefaultInParameterizedSealedHierarchyJava18() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_18_PREVIEW, () -> {
      doTest("class X {" +
             "  void x(J<Integer> j) {" +
             "    switch (j) {" +
             "      case D2 d2 -> {}" +
             "      case /*'default' branch is unnecessary*/default/*_*//**/ -> {}" +
             "    }" +
             "  }" +
             "}");
      checkQuickFix("Remove 'default' branch",
                    "class X {" +
                    "  void x(J<Integer> j) {" +
                    "    switch (j) {" +
                    "      case D2 d2 -> {}\n" +
                    "}" +
                    "  }" +
                    "}");
    });
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      """
enum E { A, B }
sealed interface I {}
final class C1 implements I {}
final class C2 implements I {}
sealed interface J<T>
final class D1 implements J<String> {}
final class D2<T> implements J<T> {}
"""
    };
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final UnnecessaryDefaultInspection inspection = new UnnecessaryDefaultInspection();
    inspection.onlyReportSwitchExpressions = false;
    return inspection;
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_17;
  }
}