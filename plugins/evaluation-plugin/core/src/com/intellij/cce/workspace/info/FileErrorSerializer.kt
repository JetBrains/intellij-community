package com.intellij.cce.workspace.info

import com.google.gson.Gson

class FileErrorSerializer {
  companion object {
    private val gson = Gson()
  }

  fun serialize(fileError: FileErrorInfo) = gson.toJson(fileError)

  fun deserialize(json: String) = gson.fromJson(json, FileErrorInfo::class.java)
}