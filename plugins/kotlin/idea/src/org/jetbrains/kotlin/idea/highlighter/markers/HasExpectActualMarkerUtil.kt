// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.highlighter.markers

import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.resolve.descriptorUtil.module

internal fun getModulesStringForExpectActualMarkerTooltip(
    descriptors: List<DeclarationDescriptor>
): String?  {
    if(descriptors.isEmpty()) return null

    fun ModuleDescriptor.nameForTooltip(): String {
        return stableName?.asStringStripSpecialMarkers() ?: "N/A"
    }

    return  when (descriptors.size) {
        1 -> descriptors.single().module.nameForTooltip()
        else -> descriptors.joinToString(", ", "[", "]") { it.module.nameForTooltip() }
    }
}
