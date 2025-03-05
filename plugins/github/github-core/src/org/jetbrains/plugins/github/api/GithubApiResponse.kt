// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api

import com.intellij.util.ThrowableConvertor
import java.io.IOException
import java.io.InputStream
import java.io.Reader

interface GithubApiResponse {
  fun findHeader(headerName: String): String?

  @Throws(IOException::class)
  fun <T> readBody(converter: ThrowableConvertor<Reader, T, IOException>): T

  @Throws(IOException::class)
  fun <T> handleBody(converter: ThrowableConvertor<InputStream, T, IOException>): T
}