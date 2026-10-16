// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources

import com.intellij.compose.ide.plugin.shared.ANDROID_MAIN
import com.intellij.compose.ide.plugin.shared.COMMON_MAIN
import com.intellij.compose.ide.plugin.shared.ComposeBaseTestCase
import com.intellij.compose.ide.plugin.shared.IOS_MAIN
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ValueSource(strings = [COMMON_MAIN, ANDROID_MAIN, IOS_MAIN])
annotation class ComposeResourcesAllSourceSets

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ValueSource(strings = [COMMON_MAIN])
annotation class ComposeResourcesCommonMainOnly

/** The base class for a code insight test on the [COMPOSE_RESOURCES_PROJECT_NAME] test data project. */
abstract class ComposeResourcesCodeInsightTestCase : ComposeBaseTestCase() {
  override val testDataProjectRoot: Path
    get() = composeResourcesProjectRoot()

  override val excludedFilePatterns: Array<String>
    get() = arrayOf("glob:**/composeApp/src/commonMain/composeResources/drawable/*.png")

  protected fun testComposeResourcesProject(test: () -> Unit) = testComposeProject(test)
}
