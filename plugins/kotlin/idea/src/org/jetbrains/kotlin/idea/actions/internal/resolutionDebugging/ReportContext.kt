// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.actions.internal.resolutionDebugging

import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.idea.base.utils.fqname.fqName
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.supertypes
import org.jetbrains.kotlin.utils.Printer

internal class ReportContext {
    private val modules: MutableSet<ModuleDescriptor> = mutableSetOf()
    private val types: MutableSet<KotlinType> = mutableSetOf()

    fun ModuleDescriptor.referenceToInstance(): ModuleReference {
        val ref = ModuleReference(instanceString(), name.toString())
        if (modules.add(this)) {
            allDependencyModules.forEach { it.referenceToInstance() }
            allExpectedByModules.forEach { it.referenceToInstance() }
        }
        return ref
    }

    fun KotlinType.referenceToInstance(): KotlinTypeReference {
        val ref = KotlinTypeReference(instanceString(), fqName.toString())
        if (types.add(this)) {
            supertypes().forEach { it.referenceToInstance() }
            this.constructor.declarationDescriptor!!.module.referenceToInstance()
        }
        return ref
    }

    fun Printer.renderContextReport() {
        println("Mentioned modules:")
        pushIndent()
        modules.forEach { descriptor ->
            ModuleDebugReport(descriptor).render(this)
            println()
        }
        popIndent()
        println()

        println("Mentioned types:")
        pushIndent()
        types.forEach { type ->
            KotlinTypeDebugReport(type).render(this)
            println()
        }
        popIndent()
        println()
    }
}
