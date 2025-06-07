// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.customElements

import com.intellij.polySymbols.customElements.json.CustomElementsManifest
import com.intellij.polySymbols.impl.objectMapper
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

@ApiStatus.Internal
fun readCustomElementsManifest(path: String): CustomElementsManifest =
  FileInputStream(File(path)).readCustomElementsManifest()

@ApiStatus.Internal
fun InputStream.readCustomElementsManifest(): CustomElementsManifest =
  objectMapper.readValue(this, CustomElementsManifest::class.java)
