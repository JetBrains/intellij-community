// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.builders

import org.jetbrains.kotlin.idea.gradleTooling.KotlinGradleVariantData
import org.jetbrains.kotlin.idea.gradleTooling.KotlinProjectModelImportingContext
import org.jetbrains.kotlin.idea.gradleTooling.get
import org.jetbrains.kotlin.idea.gradleTooling.initializeVariantStub
import org.jetbrains.kotlin.idea.projectModel.KotlinVariantData

object KotlinGradleVariantDataBuilder : KotlinProjectModelComponentBuilder<KotlinVariantData> {
    override fun buildComponent(origin: Any, importingContext: KotlinProjectModelImportingContext): KotlinVariantData? {
        //TODO if context contains fragment with the same [name + KotlinModuleIdentifier.toString] then just populate attributes
        val fragmentStub = KotlinGradleFragmentProtoBuilder.buildComponent(origin, importingContext) ?: return null

        @Suppress("UNCHECKED_CAST")
        val variantAttributes = (origin["getVariantAttributes"] as? Map<Any, String>)?.mapNotNull { (k, v) ->
            (k["getUniqueName"] as? String)?.let { it to v }
        }?.toMap().orEmpty()

        val compilationOutputs = origin["getCompilationOutputs"]
        val kotlinCompilationOutput = compilationOutputs?.let { KotlinCompilationOutputBuilder.buildComponent(it) }

        return KotlinGradleVariantData(
            variantAttributes = variantAttributes,
            compilationOutputs = kotlinCompilationOutput
        ).also { importingContext.initializeVariantStub(fragmentStub, it) }
    }
}