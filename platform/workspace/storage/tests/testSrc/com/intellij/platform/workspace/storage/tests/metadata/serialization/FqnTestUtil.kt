// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests.metadata.serialization

internal const val CACHE_VERSION_PACKAGE_NAME = "cacheVersion"

internal const val NEW_VERSION_PACKAGE_NAME = "currentVersion"


internal fun String.replaceCacheVersion(): String =
  replace(CACHE_VERSION_PACKAGE_NAME, NEW_VERSION_PACKAGE_NAME)

private fun String.replaceCurrentVersion(): String =
  replace(NEW_VERSION_PACKAGE_NAME, CACHE_VERSION_PACKAGE_NAME)


internal fun Class<*>.toCurrentVersion(): Class<*> =
  MetadataDiffTestResolver.resolveClass(name.replaceCacheVersion())

internal fun Class<*>.toCacheVersion(): Class<*> =
  MetadataDiffTestResolver.resolveClass(name.replaceCurrentVersion())
