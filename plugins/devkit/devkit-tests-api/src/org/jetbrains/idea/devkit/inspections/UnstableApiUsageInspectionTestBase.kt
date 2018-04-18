// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.util.PathUtil
import org.jetbrains.annotations.ApiStatus

abstract class UnstableApiUsageInspectionTestBase : JavaCodeInsightFixtureTestCase() {
  abstract override fun getBasePath(): String

  override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
    moduleBuilder.addLibrary("util", PathUtil.getJarPathForClass(ApiStatus.Experimental::class.java))
  }

  open fun performAdditionalSetUp() {}

  override fun setUp() {
    super.setUp()
    performAdditionalSetUp()

    myFixture.enableInspections(UnstableApiUsageInspection::class.java)

    myFixture.addFileToProject(
      "pkg/ExperimentalAnnotation.java",
      "package pkg;\n" +
      "import org.jetbrains.annotations.ApiStatus;\n" +
      "@ApiStatus.Experimental public @interface ExperimentalAnnotation {\n" +
      "  String nonExperimentalAttributeInExperimentalAnnotation() default \"\";\n" +
      "  @ApiStatus.Experimental String experimentalAttributeInExperimentalAnnotation() default \"\";\n" +
      "}"
    )
    myFixture.addFileToProject(
      "pkg/ExperimentalEnum.java",
      "package pkg;\n" +
      "\n" +
      "import org.jetbrains.annotations.ApiStatus;\n" +
      "\n" +
      "@ApiStatus.Experimental public enum ExperimentalEnum {\n" +
      "  NON_EXPERIMENTAL_VALUE_IN_EXPERIMENTAL_ENUM,\n" +
      "  @ApiStatus.Experimental EXPERIMENTAL_VALUE_IN_EXPERIMENTAL_ENUM\n" +
      "}"
    )
    myFixture.addFileToProject(
      "pkg/ExperimentalClass.java",
      "package pkg;\n" +
      "import org.jetbrains.annotations.ApiStatus;\n" +
      "@ApiStatus.Experimental public class ExperimentalClass {\n" +
      "  public static final String NON_EXPERIMENTAL_CONSTANT_IN_EXPERIMENTAL_CLASS = \"\";\n" +
      "  public String nonExperimentalFieldInExperimentalClass = \"\";\n" +
      "  public ExperimentalClass() {}\n" +
      "  public static void staticNonExperimentalMethodInExperimentalClass() {}\n" +
      "  public void nonExperimentalMethodInExperimentalClass() {}\n" +
      "\n" +
      "  @ApiStatus.Experimental public static final String EXPERIMENTAL_CONSTANT_IN_EXPERIMENTAL_CLASS = \"\";\n" +
      "  @ApiStatus.Experimental public String experimentalFieldInExperimentalClass = \"\";\n" +
      "  @ApiStatus.Experimental public ExperimentalClass(String s) {}\n" +
      "  @ApiStatus.Experimental public static void staticExperimentalMethodInExperimentalClass() {}\n" +
      "  @ApiStatus.Experimental public void experimentalMethodInExperimentalClass() {}\n" +
      "}"
    )


    myFixture.addFileToProject(
      "pkg/NonExperimentalAnnotation.java",
      "package pkg;\n" +
      "import org.jetbrains.annotations.ApiStatus;\n" +
      "public @interface NonExperimentalAnnotation {\n" +
      "  String nonExperimentalAttributeInNonExperimentalAnnotation() default \"\";\n" +
      "  @ApiStatus.Experimental String experimentalAttributeInNonExperimentalAnnotation() default \"\";\n" +
      "}"
    )
    myFixture.addFileToProject(
      "pkg/NonExperimentalEnum.java",
      "package pkg;\n" +
      "import org.jetbrains.annotations.ApiStatus;\n" +
      "public enum NonExperimentalEnum {\n" +
      "  NON_EXPERIMENTAL_VALUE_IN_NON_EXPERIMENTAL_ENUM,\n" +
      "  @ApiStatus.Experimental EXPERIMENTAL_VALUE_IN_NON_EXPERIMENTAL_ENUM\n" +
      "}"
    )
    myFixture.addFileToProject(
      "pkg/NonExperimentalClass.java",
      "package pkg;\n" +
      "import org.jetbrains.annotations.ApiStatus;\n" +
      "public class NonExperimentalClass {\n" +
      "  public static final String NON_EXPERIMENTAL_CONSTANT_IN_NON_EXPERIMENTAL_CLASS = \"\";\n" +
      "  public String nonExperimentalFieldInNonExperimentalClass = \"\";\n" +
      "  public NonExperimentalClass() {}\n" +
      "  public static void staticNonExperimentalMethodInNonExperimentalClass() {}\n" +
      "  public void nonExperimentalMethodInNonExperimentalClass() {}\n" +
      "\n" +
      "  @ApiStatus.Experimental public static final String EXPERIMENTAL_CONSTANT_IN_NON_EXPERIMENTAL_CLASS = \"\";\n" +
      "  @ApiStatus.Experimental public String experimentalFieldInNonExperimentalClass = \"\";\n" +
      "  @ApiStatus.Experimental public NonExperimentalClass(String s) {}\n" +
      "  @ApiStatus.Experimental public static void staticExperimentalMethodInNonExperimentalClass() {}\n" +
      "  @ApiStatus.Experimental public void experimentalMethodInNonExperimentalClass() {}\n" +
      "}"
    )


    myFixture.addFileToProject("unstablePkg/package-info.java",
                              "@org.jetbrains.annotations.ApiStatus.Experimental\n" +
                              "package unstablePkg;")
    myFixture.addFileToProject("unstablePkg/ClassInUnstablePkg.java",
                              "package unstablePkg;\n" +
                              "public class ClassInUnstablePkg {}")
  }
}