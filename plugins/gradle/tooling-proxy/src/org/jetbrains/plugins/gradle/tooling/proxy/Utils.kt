// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.proxy

import java.io.File

fun <T> List<T>?.nullize() = if (isNullOrEmpty()) null else this

fun File.findSequentChild(filePrefix: String, extension: String, condition: (File) -> Boolean): File {
  var postfix = 0
  val ext = if (extension.isEmpty()) "" else ".$extension"
  var candidate = File(this, filePrefix + ext)
  while (!condition.invoke(candidate)) {
    candidate = File(this, filePrefix + ++postfix + ext)
  }
  return candidate
}