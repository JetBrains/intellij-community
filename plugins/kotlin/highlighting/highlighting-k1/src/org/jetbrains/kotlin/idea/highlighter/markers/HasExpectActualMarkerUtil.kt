// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighter.markers

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analyzer.moduleInfo
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.IdeaModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.toKaModule
import org.jetbrains.kotlin.idea.codeInsight.lineMarkers.shared.nameForTooltip
import org.jetbrains.kotlin.resolve.descriptorUtil.module

@ApiStatus.Internal
fun getModulesStringForExpectActualMarkerTooltip(
    descriptors: List<DeclarationDescriptor>
): String? {
    return when (descriptors.size) {
        0 -> null
        1 -> descriptors.single().moduleNameForTooltip()
        else -> descriptors.map { it.moduleNameForTooltip() }.sorted()
            .joinToString(", ", prefix = "[", postfix = "]")
    }
}

private fun DeclarationDescriptor.moduleNameForTooltip() = (module.moduleInfo as IdeaModuleInfo?)?.toKaModule()?.nameForTooltip() ?: "N/A"
