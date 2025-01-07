// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.apiDump.lang.ast

import com.intellij.devkit.apiDump.lang.elementTypes.ADFileNodeType
import com.intellij.psi.impl.source.tree.FileElement

internal class ADFileNode(text: CharSequence?) : FileElement(ADFileNodeType, text)