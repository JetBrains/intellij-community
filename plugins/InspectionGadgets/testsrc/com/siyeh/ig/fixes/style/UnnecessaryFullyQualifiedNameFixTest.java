// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.style;

import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.style.UnnecessaryFullyQualifiedNameInspection;
import org.intellij.lang.annotations.Language;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class UnnecessaryFullyQualifiedNameFixTest extends IGQuickFixesTestCase {

  public void testLeaveFQNamesInJavadoc() {
    doTest(
      """
        /**
         * @see java.util.List
         */
        class X {  /**/java.util.List l;}""",

      """
        import java.util.List;

        /**
         * @see java.util.List
         */
        class X {  List l;}""",
      JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_ALWAYS
    );
  }

  public void testReplaceFQNamesInJavadoc() {
    doTest(
      """
        /**
         * @see java.util.List
         */
        class X {  /**/java.util.List l;}""",

      """
        import java.util.List;

        /**
         * @see List
         */
        class X {  List l;}""",
      JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED
    );
  }

  public void testReplaceFQNamesInJavadoc2() {
    doTest(
      """
        /**
         * @see java.util.List
         */
        class X {  /**/java.util.List l;}""",

      """
        import java.util.List;

        /**
         * @see List
         */
        class X {  List l;}""",
      JavaCodeStyleSettings.SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT
    );
  }

  @SuppressWarnings("DanglingJavadoc")
  public void testPackageInfo() {
    doTest(
      """
        /**
         * @see javax.annotation.Generated
         */
        @/**/javax.annotation.Generated
        package p;
        """,

      """
        /**
         * @see javax.annotation.Generated
         */
        @Generated
        package p;

        import javax.annotation.Generated;""",
      JavaCodeStyleSettings.SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT, "package-info.java");
  }

  public void testAlreadyImported() {
    doTest(InspectionGadgetsBundle.message("unnecessary.fully.qualified.name.remove.quickfix"),
           "import java.util.List;" +
           "class X {" +
           "  /**/java.util.List l;" +
           "}",

           "import java.util.List;" +
           "class X {" +
           "  List l;" +
           "}");
  }

  public void testJavaLang() {
    doTest(InspectionGadgetsBundle.message("unnecessary.fully.qualified.name.remove.quickfix"),
           "class X {" +
           "  /**/java.lang.String s;" +
           "}",

           "class X {" +
           "  String s;" +
           "}");
  }

  public void testCaretOnClassName() {
    doTest(InspectionGadgetsBundle.message("unnecessary.fully.qualified.name.remove.quickfix"),
           "class X {" +
           "  java.lang.St/**/ring s;" +
           "}",

           "class X {" +
           "  String s;" +
           "}");
  }

  private void doTest(@Language("JAVA") @NotNull @NonNls String before, @Language("JAVA") @NotNull @NonNls String after,
                      @MagicConstant(intValues = {
                        JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_ALWAYS,
                        JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED,
                        JavaCodeStyleSettings.SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT
                      }) int classNamesInJavadoc) {
    doTest(before, after, classNamesInJavadoc, "aaa.java");
  }

  private void doTest(@Language("JAVA") @NotNull @NonNls String before,
                      @Language("JAVA") @NotNull @NonNls String after,
                      @MagicConstant(intValues = {
                        JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_ALWAYS,
                        JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED,
                        JavaCodeStyleSettings.SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT
                      }) int classNamesInJavadoc,
                      String fileName) {
    final JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());
    javaSettings.CLASS_NAMES_IN_JAVADOC = classNamesInJavadoc;
    doTest(InspectionGadgetsBundle.message("unnecessary.fully.qualified.name.replace.quickfix"), before, after, fileName);
  }

  @Override
  protected BaseInspection getInspection() {
    return new UnnecessaryFullyQualifiedNameInspection();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      """
package javax.annotation;
import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;
@Documented
@Retention(SOURCE)
@Target({PACKAGE, TYPE, ANNOTATION_TYPE, METHOD, CONSTRUCTOR, FIELD,
        LOCAL_VARIABLE, PARAMETER})
public @interface Generated {
   String[] value();
   String date() default "";
   String comments() default "";
}"""
    };
  }
}
