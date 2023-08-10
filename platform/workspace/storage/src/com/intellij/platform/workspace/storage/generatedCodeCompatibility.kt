// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
object CodeGeneratorVersions {
  /** these constants are accessed from WorkspaceImplObsoleteInspection */
  private const val API_VERSION_INTERNAL = 2
  private const val IMPL_MAJOR_VERSION_INTERNAL = 2
  private const val IMPL_MINOR_VERSION_INTERNAL = 0

  @set:TestOnly
  var API_VERSION = API_VERSION_INTERNAL
  @set:TestOnly
  var IMPL_VERSION = IMPL_MAJOR_VERSION_INTERNAL

  var checkApiInInterface = true
  var checkApiInImpl = true
  var checkImplInImpl = true
}

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class GeneratedCodeApiVersion(val version: Int)

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class GeneratedCodeImplVersion(val version: Int)
