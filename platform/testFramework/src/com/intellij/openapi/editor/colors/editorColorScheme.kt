// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.colors

import java.util.regex.Pattern

fun removeSchemeMetaInfo(result: String): String {
  val matcher = Pattern.compile("\\s+<metaInfo>.*</metaInfo>", Pattern.DOTALL).matcher(result)
  if (!matcher.find()) {
    return result
  }

  val builder = StringBuilder()
  matcher.appendReplacement(builder, "")
  matcher.appendTail(builder)
  return builder.toString()
}