// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.resources.providers

import com.intellij.testFramework.junit5.resources.providers.PathInfo.Companion.cleanUpPathInfo
import org.jetbrains.annotations.TestOnly
import kotlin.reflect.KClass

/**
 * Provider creates resource [R] of [resourceType] and can also [destroy] it.
 * If [R] needs filesystem cleanup, provider might use [PathInfo.cleanUpPathInfo] if [R] is [com.intellij.openapi.util.UserDataHolder]
 */
@TestOnly
interface ResourceProvider<R : Any> {
  val resourceType: KClass<R>

  /**
   * Create [R].
   * If [R] might be created with parameters, default parameters should be used.
   * [storage] may be used to access resources, created by other providers
   */
  suspend fun create(storage: ResourceStorage): R

  /**
   * To create [R], working instance of [com.intellij.openapi.application.Application] is required
   */
  val needsApplication: Boolean
  suspend fun destroy(resource: R)
}

/**
 * The process of [R] creation may be parameterized with [P].
 * [ParameterizableResourceProvider] should still support default (parameterless) [R] creation with [create]
 * to be used by annotations (aka automatic creation).
 */
@TestOnly
interface ParameterizableResourceProvider<R : Any, P> : ResourceProvider<R> {
  suspend fun create(storage: ResourceStorage, params: P): R
}