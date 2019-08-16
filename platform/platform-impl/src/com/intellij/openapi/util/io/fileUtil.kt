// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.io

import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.PathUtilRt
import java.io.File

val File.systemIndependentPath: String
  get() = path.replace(File.separatorChar, '/')

// PathUtilRt.getParentPath returns empty string if no parent path, but in Kotlin "null" is better because elvis operator could be used
fun getParentPath(path: String): String? = StringUtil.nullize(PathUtilRt.getParentPath(path))

fun endsWithSlash(path: String): Boolean = path.getOrNull(path.length - 1) == '/'

fun endsWithName(path: String, name: String): Boolean =
  path.endsWith(name) && (path.length == name.length || path.getOrNull(path.length - name.length - 1) == '/')