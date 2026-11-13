// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections.path

import com.intellij.openapi.application.EDT
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.idea.devkit.inspections.PathAnnotationInspectionFixture
import org.jetbrains.idea.devkit.inspections.pathAnnotationInspectionFixture
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor

/**
 * Creates the project of a Kotlin path annotation test class: a project with the full JDK and the Kotlin standard library.
 * Declare it in a companion object, and give it to [pathAnnotationInspectionKotlinFixture].
 */
fun pathAnnotationInspectionKotlinProjectFixture(): TestFixture<LightJavaProject> =
  lightJavaProjectFixture(KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceFullJdk())

/**
 * Creates a [PathAnnotationInspectionFixture] for one test with Kotlin test files. Declare it as an instance field.
 * The fixture adds the Kotlin runtime to the module.
 */
fun pathAnnotationInspectionKotlinFixture(projectFixture: TestFixture<LightJavaProject>): TestFixture<PathAnnotationInspectionFixture> =
  testFixture("pathAnnotationInspectionKotlinFixture") {
    val pathAnnotation = pathAnnotationInspectionFixture(projectFixture, "kt").init()
    withContext(Dispatchers.EDT) {
      ConfigLibraryUtil.configureKotlinRuntime(pathAnnotation.fixture.module)
    }
    IndexingTestUtil.suspendUntilIndexesAreReady(pathAnnotation.fixture.project)
    initialized(pathAnnotation) {}
  }
