// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.editor

import com.google.gson.Gson

class GsonComplexPathSerializer<P : ComplexPathVirtualFileSystem.ComplexPath>(
  private val pathClass: Class<P>,
  private val gson: Gson = Gson()
) : ComplexPathVirtualFileSystem.ComplexPathSerializer<P> {
  override fun serialize(path: P): String = gson.toJson(path)

  override fun deserialize(rawPath: String): P = gson.fromJson(rawPath, pathClass)
}