// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.io

import java.io.File

val File.systemIndependentPath: String
  get() = path.replace(File.separatorChar, '/')

fun endsWithName(path: String, name: String): Boolean {
  return path.endsWith(name) && (path.length == name.length || path.getOrNull(path.length - name.length - 1) == '/')
}