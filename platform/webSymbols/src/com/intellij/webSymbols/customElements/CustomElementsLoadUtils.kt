// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.customElements

import com.intellij.webSymbols.customElements.json.CustomElementsManifest
import com.intellij.webSymbols.impl.objectMapper
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

fun readCustomElementsManifest(path: String): CustomElementsManifest =
  FileInputStream(File(path)).readCustomElementsManifest()

@ApiStatus.Internal
fun InputStream.readCustomElementsManifest(): CustomElementsManifest =
  objectMapper.readValue(this, CustomElementsManifest::class.java)
