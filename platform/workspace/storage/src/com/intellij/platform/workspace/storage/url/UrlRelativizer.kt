// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.url

/**
 * The UrlRelativizer interface is used to convert between absolute and relative file paths.
 * Used by [com.intellij.platform.workspace.storage.impl.EntityStorageSerializerImpl] for entity
 * storage serialization, so that the paths in the cache are relative (instead of absolute).
 */
public interface UrlRelativizer {

  /**
   * Converts an absolute URL to a relative URL.
   * Details of conversion depend on the implementation.
   *
   * @param url The absolute URL to be converted.
   * @return The relative URL. If the absolute URL could not be
   *  made to a relative one, a passed `url` will be returned.
   * @see toAbsoluteUrl
   */
  public fun toRelativeUrl(url: String): String

  /**
   * Converts a relative URL to an absolute URL.
   * Details of conversion depend on the implementation.
   *
   * @param url The relative URL to be converted.
   * @return The absolute URL. If the relative URL could not be
   *  made to an absolute one, a passed `url` will be returned.
   * @see toRelativeUrl
   */
  public fun toAbsoluteUrl(url: String): String

}