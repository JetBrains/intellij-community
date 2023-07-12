// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.url

/**
 * The UrlRelativizer interface is used to convert between absolute and relative file paths.
 * Used by [com.intellij.platform.workspace.storage.impl.EntityStorageSerializerImpl] for entity
 * storage serialization, so that the paths in the cache are relative (instead of absolute).
 */
interface UrlRelativizer {

  fun toRelativeUrl(url: String): String

  fun toAbsoluteUrl(url: String): String

}