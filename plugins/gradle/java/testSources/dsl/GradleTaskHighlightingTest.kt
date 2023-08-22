// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.codeInspection.GradleDisablerTestUtils
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyAccessibilityInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.junit.jupiter.params.ParameterizedTest

class GradleTaskHighlightingTest : GradleCodeInsightTestCase() {

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test task declaration`(gradleVersion: GradleVersion) {
    Disposer.newDisposable().use { parentDisposable ->
      testEmptyProject(gradleVersion) {
        GradleDisablerTestUtils.enableAllDisableableInspections(parentDisposable)
        codeInsightFixture.enableInspections(
          GrUnresolvedAccessInspection::class.java,
          GroovyAssignabilityCheckInspection::class.java,
          GroovyAccessibilityInspection::class.java
        )
        testHighlighting("""
          |task(id1)
          |task(id2) {}
          |task(id3, {})
          |task(id4, description: 'oh')
          |task(id5, description: 'oh') {}
          |task(id6, description: 'oh', {})
          |
          |task id7()
          |task id8 {}
          |task id9() {}
          |task id10({})
          |
          |task id11(type: Copy)
          |task id12(type: Copy) {}
          |task id13(type: Copy, {})
          |//task mid11([type: Copy])    // invalid
          |task mid12([type: Copy]) {}
          |task mid13([type: Copy], {})
          |//task emid11([:])            // invalid
          |task emid12([:]) {}
          |task emid13([:], {})
          |
          |task id14 << {}
        """.trimMargin())
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `task declaration invalid`(gradleVersion: GradleVersion) {
    Disposer.newDisposable().use { parentDisposable ->
      testEmptyProject(gradleVersion) {
        GradleDisablerTestUtils.enableAllDisableableInspections(parentDisposable)
        codeInsightFixture.enableInspections(
          GrUnresolvedAccessInspection::class.java,
          GroovyAssignabilityCheckInspection::class.java,
          GroovyAccessibilityInspection::class.java
        )
        testHighlighting("""
          |task <warning descr="'task' in 'org.gradle.api.Project' cannot be applied to '(java.lang.String, java.lang.Integer)'">id1, 42</warning>
          |task <warning descr="'task' in 'org.gradle.api.Project' cannot be applied to '(java.lang.Integer, ?)'">42, <warning descr="Cannot resolve symbol 'id2'">id2</warning></warning>
          |task <warning descr="'task' in 'org.gradle.api.Project' cannot be applied to '(java.lang.String, java.lang.Integer, java.lang.Integer)'">id3, 42, 43</warning>
          |task <warning descr="'task' in 'org.gradle.api.Project' cannot be applied to '(?, java.lang.Integer, java.lang.Integer, java.lang.Integer)'"><warning descr="Cannot resolve symbol 'id4'">id4</warning>, 42, 43, 69</warning>
          |task <warning descr="'task' in 'org.gradle.api.Project' cannot be applied to '(['description':java.lang.String], java.lang.String, java.lang.Integer)'">id5, description: 'a', 43</warning>
          |task <warning descr="'task' in 'org.gradle.api.Project' cannot be applied to '(['description':java.lang.String], ?, java.lang.Integer, java.lang.Integer)'"><warning descr="Cannot resolve symbol 'id6'">id6</warning>, description: 'a', 43, 69</warning>
          |
          |task <weak_warning descr="Cannot infer argument types"><warning descr="Cannot resolve symbol 'id7'">id7</warning>(42)</weak_warning>
          |task<warning descr="'task' in 'org.gradle.api.Project' cannot be applied to '(groovy.lang.Closure<java.lang.Void>, ?)'">({}, <warning descr="Cannot resolve symbol 'id8'">id8</warning>)</warning>
          |
          |task id9 + {}
          |
          |task <weak_warning descr="Cannot infer argument types"><warning descr="Cannot resolve symbol 'mid11'">mid11</warning>([type: Copy])</weak_warning>
          |task <weak_warning descr="Cannot infer argument types"><warning descr="Cannot resolve symbol 'emid11'">emid11</warning>([:])</weak_warning>
        """.trimMargin())
      }
    }
  }
}