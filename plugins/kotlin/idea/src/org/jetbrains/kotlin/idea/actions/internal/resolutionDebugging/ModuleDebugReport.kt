// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.actions.internal.resolutionDebugging

import org.jetbrains.kotlin.analyzer.moduleInfo
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibraryInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibrarySourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.SdkInfo
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.utils.Printer

internal data class ModuleReference(val instanceString: String, val shortName: String) {
    override fun toString(): String = "$instanceString{$shortName}"
}

internal class ModuleDebugReport(
    val reference: ModuleReference,
    val moduleDescriptorToString: String,
    val moduleInfoToString: String?,
    val ijModuleToString: String?,
    val platform: TargetPlatform?,
    val dependenciesRefs: Collection<ModuleReference>,
    val dependsOnRefs: Collection<ModuleReference>,
) {
    fun render(printer: Printer): Unit = with(printer) {
        val isBuiltIns = moduleDescriptorToString.contains("built-ins")

        println("Instance = $reference")
        println("Module descriptor toString = $moduleDescriptorToString")

        if (moduleInfoToString != null) {
            println("Module info toString = $moduleInfoToString")
        } else if (isBuiltIns) {
            println("Built-ins module doesn't have a ModuleInfo")
        } else {
            println(
                "Couldn't find ModuleInfo capability in the descriptor. This shouldn't be happening normally. " +
                        "Check org.jetbrains.kotlin.analyzer.AbstractResolverForProject.createModuleDescriptor"
            )
        }


        if (ijModuleToString != null) {
            println("IJ entity toString = $ijModuleToString")
        } else if (isBuiltIns) {
            println("Built-ins module doesn't have an IJ-module")
        } else {
            println(
                "Couldn't find IJ model entity for given Module Info. " +
                        "This can happen if the case chasing in the debug action misses some case." +
                        "Please, check the type of ModuleInfo and improve the code in " +
                        "`org.jetbrains.kotlin.idea.actions.internal.resolutionDebugging.DebugTypeMismatchAction.moduleDebugReport`"
            )
        }

        println("Platform = ${platform}")

        println("Dependencies:")
        pushIndent()
        dependenciesRefs.forEach { println(it) }
        popIndent()
        println()

        if (dependsOnRefs.isNotEmpty()) {
            println("DependsOn:")
            pushIndent()
            dependsOnRefs.forEach { println(it) }
            popIndent()
            println()
        } else {
            println("No dependsOn edges")
        }
    }

    // only [reference] is included into equals/hashcode
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ModuleDebugReport

        if (reference != other.reference) return false

        return true
    }

    override fun hashCode(): Int {
        return reference.hashCode()
    }

    override fun toString(): String {
        return "ModuleDebugReport(reference=$reference)"
    }
}

internal fun ReportContext.ModuleDebugReport(descriptor: ModuleDescriptor): ModuleDebugReport {
    val moduleInfo = descriptor.moduleInfo

    val ijModule: Any? = when (moduleInfo) {
        is ModuleSourceInfo -> moduleInfo.module
        is LibraryInfo -> moduleInfo.library
        is LibrarySourceInfo -> moduleInfo.library
        is SdkInfo -> moduleInfo.sdk
        else -> null
    }

    return ModuleDebugReport(
        descriptor.referenceToInstance(),
        moduleDescriptorToString = descriptor.toString(),
        moduleInfoToString = moduleInfo?.toString(),
        ijModuleToString = ijModule?.toString(),
        platform = descriptor.platform,
        dependenciesRefs = descriptor.allDependencyModules.map { it.referenceToInstance() },
        dependsOnRefs = descriptor.allExpectedByModules.map { it.referenceToInstance() }
    )
}
