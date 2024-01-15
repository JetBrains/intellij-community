// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
public object CodeGeneratorVersions {
  /** these constants are accessed from WorkspaceImplObsoleteInspection */
  private const val API_VERSION_INTERNAL = 2
  private const val IMPL_MAJOR_VERSION_INTERNAL = 3
  private const val IMPL_MINOR_VERSION_INTERNAL = 9

  @set:TestOnly
  public var API_VERSION: Int = API_VERSION_INTERNAL
  @set:TestOnly
  public var IMPL_VERSION: Int = IMPL_MAJOR_VERSION_INTERNAL

  public var checkApiInInterface: Boolean = true
  public var checkApiInImpl: Boolean = true
  public var checkImplInImpl: Boolean = true
}

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class GeneratedCodeApiVersion(val version: Int)

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class GeneratedCodeImplVersion(val version: Int)
