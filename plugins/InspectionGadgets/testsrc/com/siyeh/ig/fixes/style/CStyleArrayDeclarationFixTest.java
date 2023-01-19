// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.style;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.style.CStyleArrayDeclarationInspection;

/**
 * @author Bas Leijdekkers
 */
public class CStyleArrayDeclarationFixTest extends IGQuickFixesTestCase {

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder builder) throws Exception {
    super.tuneFixture(builder);
    builder.setLanguageLevel(LanguageLevel.HIGHEST);
  }

  public void testMethod() { doTest(); }
  public void testLocalVariable() {
    doTest();
  }
  public void testField() { doTest(); }
  public void testInForLoop() { doTest(); }
  public void testMultipleVariablesSingleDeclaration() { doTest(); }
  public void testMultipleFieldsSingleDeclaration() { doTest(); }
  public void testParameter() { doTest(); }
  public void testRecord() { doTest(); }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[]{
      """
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.TYPE_USE})
public @interface Required {
}""",

      """
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.TYPE_USE})
public @interface Preliminary {
}"""
    };
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new CStyleArrayDeclarationInspection());
    myRelativePath = "style/cstyle_array_declaration";
    myDefaultHint = "Fix all 'C-style array declaration' problems in file";
  }
}
