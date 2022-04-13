// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.deft.codegen.model

class KtAnnotation(val name: SrcRange, val args: List<SrcRange>) {
    override fun toString(): String = "@${name.text}(${args.joinToString { it.text }})"
}