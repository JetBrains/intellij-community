// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.state

import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json

/**
 * The format of a [TransferableObjectStateProperty] without a custom serializer.
 */
val commonFormat: StringFormat = Json { allowSpecialFloatingPointValues = true }
