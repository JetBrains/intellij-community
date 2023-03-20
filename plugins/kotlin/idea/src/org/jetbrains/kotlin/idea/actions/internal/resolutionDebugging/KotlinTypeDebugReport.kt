// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.actions.internal.resolutionDebugging

import org.jetbrains.kotlin.idea.base.utils.fqname.fqName
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.supertypes
import org.jetbrains.kotlin.utils.Printer

internal data class KotlinTypeReference(val instanceString: String, val fqn: String) {
    override fun toString(): String = "$instanceString{$fqn}"
}

internal class KotlinTypeDebugReport(
  val typeReference: KotlinTypeReference,
  val fqn: String?,
  private val containingModule: ModuleReference,
  private val supertypesReferences: Collection<KotlinTypeReference>
) {
    fun render(printer: Printer): Unit = with(printer) {
        println("Instance = $typeReference")
        println("fqn = $fqn")
        println("Containing module = $containingModule")
        println("Supertypes")
        pushIndent()
        supertypesReferences.forEach { println(it) }
        popIndent()
    }

    // only [reference] in included into equality/toString
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KotlinTypeDebugReport

        if (typeReference != other.typeReference) return false

        return true
    }

    override fun hashCode(): Int {
        return typeReference.hashCode()
    }

    override fun toString(): String {
        return "KotlinTypeDebugReport(typeReference=$typeReference)"
    }
}

internal fun ReportContext.KotlinTypeDebugReport(type: KotlinType): KotlinTypeDebugReport {
    return KotlinTypeDebugReport(
        type.referenceToInstance(),
        type.fqName?.toString(),
        type.constructor.declarationDescriptor!!.module.referenceToInstance(),
        type.supertypes().map { it.referenceToInstance() }
    )
}
