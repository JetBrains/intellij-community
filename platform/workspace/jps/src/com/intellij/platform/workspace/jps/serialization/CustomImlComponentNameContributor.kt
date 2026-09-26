// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.serialization

import org.jetbrains.annotations.ApiStatus

/**
 * Declares a persistent component name handled by `CustomImlComponentService`.
 *
 * Registering a contributor allows using the provided [componentName] to
 * read and write persistent module-level components via `CustomImlComponentService`.
 *
 * [CustomImlComponentNameContributor] is an extension point, so it must be registered
 * in the corresponding `plugin.xml` file under the tag `<workspaceModel.customImlComponentNameContributor implementation="..."/>`.
 */
@ApiStatus.Experimental
interface CustomImlComponentNameContributor {
  /**
   *  Name of the persistent component.
   */
  val componentName: String
}