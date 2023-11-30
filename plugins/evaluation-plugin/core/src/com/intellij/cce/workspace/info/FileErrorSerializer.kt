// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.workspace.info

import com.google.gson.Gson

class FileErrorSerializer {
  companion object {
    private val gson = Gson()
  }

  fun serialize(fileError: FileErrorInfo) = gson.toJson(fileError)

  fun deserialize(json: String) = gson.fromJson(json, FileErrorInfo::class.java)
}