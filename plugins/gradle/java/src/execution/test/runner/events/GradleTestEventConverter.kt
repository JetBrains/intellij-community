// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner.events

import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Allows converting and repairing test event data.
 *
 * The only point behind this EP is making gradle code not tied up with groovy one.
 * If someone decides to make this API public (i.e. the API obtains more clients), then they should rethink this approach.
 * One of the possible solutions may be built [in this way](https://upsource.jetbrains.com/intellij/review/IDEA-CR-56127).
 */
@ApiStatus.Internal
internal interface GradleTestEventConverter {

  fun getClassName(): String

  fun getMethodName(): String?

  fun getDisplayName(): String

  @ApiStatus.Internal
  interface Factory {

    fun createConverter(
      project: Project,
      parent: SMTestProxy,
      isSuite: Boolean,
      suiteName: String,
      className: String,
      methodName: String?,
      displayName: String
    ): GradleTestEventConverter?
  }

  companion object {

    private val EP_NAME = ExtensionPointName.create<Factory>("org.jetbrains.plugins.gradle.testEventConverter")

    @JvmStatic
    fun getInstance(
      project: Project,
      parent: SMTestProxy,
      isSuite: Boolean,
      suiteName: String,
      className: String,
      methodName: String?,
      displayName: String
    ): GradleTestEventConverter {
      return EP_NAME.extensionList.firstNotNullOfOrNull {
        it.createConverter(project, parent, isSuite, suiteName, className, methodName, displayName)
      } ?: DefaultGradleTestEventConverter(isSuite, className, methodName, displayName)
    }
  }
}