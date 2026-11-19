// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.kotlin

import com.intellij.junit.testFramework.JUnitProjectDescriptor
import com.intellij.junit.testFramework.MavenTestLib
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import org.jetbrains.kotlin.idea.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.addRoot

/**
 * Creates the project of a Kotlin JUnit test class, as [com.intellij.junit.testFramework.junitProjectFixture] does with [libraries].
 * The module also has the Kotlin standard library and `kotlin-test`, as [ConfigLibraryUtil.configureKotlinRuntime] adds them.
 * Declare it in a companion object.
 */
fun kotlinJUnitProjectFixture(vararg libraries: MavenTestLib): TestFixture<LightJavaProject> =
  lightJavaProjectFixture(KotlinRuntimeJUnitProjectDescriptor(libraries.toSet()))

private class KotlinRuntimeJUnitProjectDescriptor(libraries: Set<MavenTestLib>) : JUnitProjectDescriptor(LanguageLevel.HIGHEST, libraries) {
  override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
    super.configureModule(module, model, contentEntry)
    ConfigLibraryUtil.addLibrary(model, "KOTLIN_STDLIB_LIB_NAME") {
      addRoot(TestKotlinArtifacts.kotlinStdlib, OrderRootType.CLASSES)
    }
    ConfigLibraryUtil.addLibrary(model, "KOTLIN_TEST_LIB_NAME") {
      addRoot(TestKotlinArtifacts.kotlinTest, OrderRootType.CLASSES)
    }
  }
}
