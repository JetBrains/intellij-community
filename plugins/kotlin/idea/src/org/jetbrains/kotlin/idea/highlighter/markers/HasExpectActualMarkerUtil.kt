// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.highlighter.markers

import org.jetbrains.kotlin.analyzer.moduleInfo
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.MemberDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.idea.base.facet.isHMPPEnabled
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.resolve.descriptorUtil.module

internal fun getModulesStringForExpectActualMarkerTooltip(
    descriptors: List<DeclarationDescriptor>
): String? {
    if (descriptors.isEmpty()) return null
    val isExpectDescriptors = descriptors.all { it is MemberDescriptor && it.isExpect }

    fun ModuleDescriptor.nameForTooltip(): String {
        /* For hmpp modules, prefer the module name, if present */
        moduleInfo?.let { it as? ModuleSourceInfo }?.takeIf { it.module.isHMPPEnabled }?.module?.name?.let { return it }
        stableName?.asStringStripSpecialMarkers()?.let { return it }

        /* Handle non-hmpp modes where no stableName might be present: */

        // There is no 'easy' way to figure out the exact common module in this case
        if (isExpectDescriptors) return "?common?"

        // We want to represent actual descriptors, so let's represent them by platform
        this.platform?.componentPlatforms?.joinToString(", ", "{", "}") { it.platformName }?.let { return it }

        // We did our best, what can you do, this is life for you!
        return "N/A"
    }

    return when (descriptors.size) {
        1 -> descriptors.single().module.nameForTooltip()
        else -> descriptors.joinToString(", ", "[", "]") { it.module.nameForTooltip() }
    }
}
