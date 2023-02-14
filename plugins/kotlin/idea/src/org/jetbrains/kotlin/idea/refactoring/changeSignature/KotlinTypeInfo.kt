// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.changeSignature

import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.types.KotlinType

data class KotlinTypeInfo(val isCovariant: Boolean, val type: KotlinType? = null, val text: String? = null)

fun KotlinTypeInfo.render(): String = when {
    text != null -> text
    type != null -> renderType()
    else -> ""
}

private fun KotlinTypeInfo.renderType(): String {
    val renderer = if (isCovariant) IdeDescriptorRenderers.SOURCE_CODE else IdeDescriptorRenderers.SOURCE_CODE_NOT_NULL_TYPE_APPROXIMATION
    return renderer.renderType(type!!)
}

fun KotlinTypeInfo.isEquivalentTo(other: KotlinTypeInfo): Boolean {
    return if (type != null && other.type != null) renderType() == other.renderType() else text == other.text
}