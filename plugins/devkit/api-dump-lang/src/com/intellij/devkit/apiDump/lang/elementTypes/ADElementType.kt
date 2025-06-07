// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.apiDump.lang.elementTypes

import com.intellij.psi.tree.IElementType
import com.intellij.devkit.apiDump.lang.ADLanguage

internal class ADElementType(debugName: String) : IElementType(debugName, ADLanguage) {
}