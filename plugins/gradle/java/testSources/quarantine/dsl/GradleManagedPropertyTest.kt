// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.quarantine.dsl

import com.intellij.psi.CommonClassNames.JAVA_LANG_STRING
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_PROVIDER_PROPERTY
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatGradleIsAtLeast
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyAccessibilityInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.junit.jupiter.params.ParameterizedTest

class GradleManagedPropertyTest : GradleCodeInsightTestCase() {

  @ParameterizedTest
  @AllGradleVersionsSource("""
      "<caret>myExt"                                : pkg.MyExtension,
      "myExt.<caret>stringProperty"                 : $GRADLE_API_PROVIDER_PROPERTY<$JAVA_LANG_STRING>,
      "myExt.getStringProperty(<caret>)"            : $GRADLE_API_PROVIDER_PROPERTY<$JAVA_LANG_STRING>,
      "myExt.getStringProperty().get(<caret>)"      : $JAVA_LANG_STRING,
      "myExt { <caret>delegate }"                   : pkg.MyExtension,
      "myExt { <caret>stringProperty }"             : $GRADLE_API_PROVIDER_PROPERTY<$JAVA_LANG_STRING>,
      "myExt { getStringProperty(<caret>) }"        : $GRADLE_API_PROVIDER_PROPERTY<$JAVA_LANG_STRING>,
      "myExt { getStringProperty().get(<caret>) }"  : $JAVA_LANG_STRING
  """)
  fun types(gradleVersion: GradleVersion, expression: String, type: String) {
    assumeThatGradleIsAtLeast(gradleVersion, "6.0")
    test(gradleVersion, FIXTURE_BUILDER) {
      testBuildscript(expression) {
        typingTest(elementUnderCaret(GrExpression::class.java), type)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun highlighting(gradleVersion: GradleVersion) {
    assumeThatGradleIsAtLeast(gradleVersion, "6.0")
    test(gradleVersion, FIXTURE_BUILDER) {
      fixture.enableInspections(
        GrUnresolvedAccessInspection::class.java,
        GroovyAssignabilityCheckInspection::class.java,
        GroovyAccessibilityInspection::class.java
      )
      updateProjectFile("""
        |myExt.integerProperty = 42
        |<warning descr="Cannot assign 'Object' to 'Integer'">myExt.integerProperty</warning> = new Object()
        |myExt.setIntegerProperty(69)
        |myExt.setIntegerProperty<warning descr="'setIntegerProperty' in 'pkg.MyExtension' cannot be applied to '(java.lang.Object)'">(new Object())</warning>
        |myExt {
        |  integerProperty = 42
        |  <warning descr="Cannot assign 'Object' to 'Integer'">integerProperty</warning> = new Object()
        |  setIntegerProperty(69)
        |  setIntegerProperty<warning descr="'setIntegerProperty' in 'pkg.MyExtension' cannot be applied to '(java.lang.Object)'">(new Object())</warning>
        |}
      """.trimMargin())
      fixture.checkHighlighting()
    }
  }

  companion object {

    private val FIXTURE_BUILDER = GradleTestFixtureBuilder.create("GradleManagedPropertyTest") { gradleVersion ->
      withFile("buildSrc/build.gradle", "")
      withFile("buildSrc/src/main/java/pkg/MyExtension.java", """
        |package pkg;
        |
        |import org.gradle.api.provider.Property;
        |
        |public abstract class MyExtension {
        |  abstract Property<String> getStringProperty();
        |  Property<Integer> getIntegerProperty() {
        |    throw new RuntimeException();
        |  }
        |}
      """.trimMargin())
      withBuildFile(gradleVersion) {
        addPostfix("project.extensions.create('myExt', pkg.MyExtension)")
      }
      withSettingsFile(gradleVersion) {
        setProjectName("GradleManagedPropertyTest")
      }
    }
  }
}