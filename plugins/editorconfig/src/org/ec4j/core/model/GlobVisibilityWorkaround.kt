// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.ec4j.core.model

class GlobVisibilityWorkaround private constructor(source: String) : Glob(source) {
  companion object {
    fun convertGlobToRegEx(globString: String): String {
      val result = StringBuilder()
      // We need this method, but it is package-private in org.ec4j.core.model
      convertGlobToRegEx(globString, arrayListOf(), result)
      return result.toString()
    }
  }
}