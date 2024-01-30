// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.service.fus.collectors

enum class DocumentationLinkProtocol {
  HTTP,
  HTTPS,
  PSI_ELEMENT,
  FILE,
  OTHER;

  companion object {
    fun of(url: String): DocumentationLinkProtocol {
      val prefix = url.takeWhile { it != ':' }
      return entries.firstOrNull { it.name.equals(prefix, true) }
             ?: OTHER
    }
  }
}