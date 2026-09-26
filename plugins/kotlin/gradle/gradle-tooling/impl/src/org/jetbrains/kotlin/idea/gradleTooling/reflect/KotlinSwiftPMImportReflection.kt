// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling.reflect

fun KotlinSwiftPMImportReflection(swiftPMImportIdeContext: Any): KotlinSwiftPMImportReflection = KotlinSwiftPMImportReflectionImpl(swiftPMImportIdeContext)

interface KotlinSwiftPMImportReflection {
    val hasSwiftPMDependencies: Boolean
    val integrateLinkagePackageTaskPath: String
    val magicPackageName: String
}

private class KotlinSwiftPMImportReflectionImpl(private val instance: Any) : KotlinSwiftPMImportReflection {
    override val hasSwiftPMDependencies: Boolean
        get() = instance.callReflectiveAnyGetter("getHasSwiftPMDependencies", logger) as Boolean

    override val integrateLinkagePackageTaskPath: String
        get() = instance.callReflectiveAnyGetter("getIntegrateLinkagePackageTaskPath", logger) as String

    override val magicPackageName: String
        get() = instance.callReflectiveAnyGetter("getMagicPackageName", logger) as String

    companion object {
        private val logger = ReflectionLogger(KotlinSwiftPMImportReflection::class.java)
    }
}
