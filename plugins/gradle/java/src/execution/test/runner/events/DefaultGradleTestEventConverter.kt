// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner.events

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class DefaultGradleTestEventConverter(
  private val isSuite: Boolean,
  private val className: String,
  private val methodName: String?,
  private val displayName: String
) : GradleTestEventConverter {

  override fun getClassName(): String {
    return className
  }

  override fun getMethodName(): String? {
    return methodName
  }

  override fun getDisplayName(): String {
    return when (isSuite) {
      true -> extractName(displayName, JUNIT_4_CLASS_EXTRACTOR)
              ?: displayName
      else -> extractName(displayName, TEST_LAUNCHER_METHOD_EXTRACTOR)
              ?: extractName(displayName, JUNIT_5_METHOD_EXTRACTOR)
              ?: displayName
    }
  }

  companion object {

    private val JUNIT_4_CLASS_EXTRACTOR = ".*\\.([^.]+)".toRegex()
    private val TEST_LAUNCHER_METHOD_EXTRACTOR = "Test (.+)\\(\\)\\(.+\\)".toRegex()
    private val JUNIT_5_METHOD_EXTRACTOR = "(.+)\\(\\)".toRegex()

    fun extractName(name: String, extractor: Regex): String? {
      val result = extractor.findAll(name).firstOrNull() ?: return null
      if (result.groupValues.firstOrNull() != name) {
        return null
      }
      return result.groupValues.drop(1).joinToString("")
    }
  }
}