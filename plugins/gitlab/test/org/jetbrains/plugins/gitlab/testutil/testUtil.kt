// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.testutil

import com.intellij.openapi.application.PathManager
import kotlinx.coroutines.*
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.nio.file.Path
import kotlin.io.path.exists

fun getGitLabTestDataPath(at: String): Path? =
  Path.of(PathManager.getHomePath(), at).takeIf { it.exists() } ?:
  Path.of(PathManager.getHomePath()).parent.resolve(at).takeIf { it.exists() }

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(private val dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()) : TestRule {

  override fun apply(base: Statement, description: Description): Statement {
    return object : Statement() {
      override fun evaluate() {
        Dispatchers.setMain(dispatcher)
        try {
          base.evaluate()
        }
        finally {
          Dispatchers.resetMain()
        }
      }
    }
  }
}