// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase


class UsagesOfObsoleteApiInspectionTest : LightJavaCodeInsightFixtureTestCase() {

  override fun setUp() {
    super.setUp()
    myFixture.addClass("""
      package org.jetbrains.annotations;
      
      import java.lang.annotation.*;
      
      public class ApiStatus { 
        @Documented
        @Retention(RetentionPolicy.CLASS)
        @Target({
          ElementType.TYPE, ElementType.ANNOTATION_TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.PACKAGE
        })
        public @interface Obsolete {
          String since() default "";
        }
      }
  """.trimIndent())
    myFixture.enableInspections(UsagesOfObsoleteApiInspection())
  }

  fun testJavaUsagesAndOverriding() {
    myFixture.addClass("class A {@org.jetbrains.annotations.ApiStatus.Obsolete void f() {}}")
    myFixture.configureByText("b.java",
                                        "class B {" +
                                        "  void f(A a) {" +
                                        "    a.<text_attr descr=\"Obsolete API is used\">f</text_attr>();" +
                                        "  }" +
                                        "}" +
                                        "class C extends A {" +
                                        "   void <text_attr descr=\"Obsolete API is used\">f</text_attr>() {}" +
                                        "}" +
                                        "@org.jetbrains.annotations.ApiStatus.Obsolete class D extends A {" +
                                        "   void <text_attr descr=\"Obsolete API is used\">f</text_attr>() {}" +
                                        "}")
    myFixture.checkHighlighting(true, true, true)
  }
}