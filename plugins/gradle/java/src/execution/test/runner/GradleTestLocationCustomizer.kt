// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner

import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Allows to define custom location for test nodes under Gradle test execution.
 *
 * The only point behind this EP is making gradle code not tied up with groovy one.
 * If someone decides to make this API public (i.e. the API obtains more clients), then they should rethink this approach.
 * One of the possible solutions may be built [in this way](https://upsource.jetbrains.com/intellij/review/IDEA-CR-56127).
 */
@ApiStatus.Internal
internal interface GradleTestLocationCustomizer {
  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<GradleTestLocationCustomizer> = ExtensionPointName.create("org.jetbrains.plugins.gradle.testLocationCustomizer")
  }

  data class GradleTestLocationInfo(val fqClassName: String, val methodName : String?)

  /**
   * Produces test framework-specific test location by the data of a test node.
   *
   * @param parent direct parent of a customizable test node
   * @param fqClassName fully-qualified name of the class containing a test method of a customizable test node
   * @param methodName name of a test method of a customizable test node
   * @param displayName name of a customizable test node that will be displayed in the UI
   */
  fun getLocationInfo(project: Project, parent: SMTestProxy, fqClassName: String, methodName: String?, displayName: String?): GradleTestLocationInfo?
}