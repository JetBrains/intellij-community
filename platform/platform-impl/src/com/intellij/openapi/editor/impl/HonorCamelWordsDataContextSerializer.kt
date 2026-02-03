// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.ide.CustomDataContextSerializer
import com.intellij.openapi.actionSystem.DataKey
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer

internal class HonorCamelWordsDataContextSerializer: CustomDataContextSerializer<Boolean> {
  override val key: DataKey<Boolean> = CaretImpl.HONOR_CAMEL_WORDS
  override val serializer: KSerializer<Boolean> = Boolean.serializer()
}