// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.uast

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.platform.uast.testFramework.env.UastFixture
import com.intellij.platform.uast.testFramework.env.uastFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import org.jetbrains.uast.UFile

/**
 * Creates a [UastFixture] with the test data of the Groovy UAST tests. Declare it as an instance field.
 *
 * [check] is the check of [UastFixture.doTest] without a callback.
 */
fun groovyUastFixture(
  projectFixture: TestFixture<LightJavaProject>,
  check: ((String, UFile) -> Unit)? = null,
): TestFixture<UastFixture> =
  uastFixture(projectFixture, PathManagerEx.getCommunityHomePath() + "/plugins/groovy/groovy-uast-tests/testData", check)
