// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import com.intellij.util.containers.prefix.map.AbstractPrefixTreeFactory
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object GradlePathPrefixTreeFactory : AbstractPrefixTreeFactory<String, String>() {

  override fun convertToList(element: String): List<String> {
    return element.split(":")
  }
}