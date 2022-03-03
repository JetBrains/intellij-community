// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.ranker.local

import com.intellij.internal.ml.models.local.ZipModelMetadataReader
import java.util.zip.ZipFile

internal class ZipCompletionRankingModelMetadataReader(zipFile: ZipFile): ZipModelMetadataReader(zipFile) {
  fun getSupportedLanguages(): List<String> = resourceContent("languages.txt").lines()
}
