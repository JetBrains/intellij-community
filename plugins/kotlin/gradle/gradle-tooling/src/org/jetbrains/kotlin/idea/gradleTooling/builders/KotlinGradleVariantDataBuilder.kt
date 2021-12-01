// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.builders

import org.jetbrains.kotlin.idea.gradleTooling.KotlinGradleVariantData
import org.jetbrains.kotlin.idea.gradleTooling.KotlinProjectModelImportingContext
import org.jetbrains.kotlin.idea.gradleTooling.initializeVariantStub
import org.jetbrains.kotlin.idea.gradleTooling.reflect.kpm.KotlinVariantReflection
import org.jetbrains.kotlin.idea.projectModel.KotlinVariantData

object KotlinGradleVariantDataBuilder : KotlinProjectModelComponentBuilder<KotlinVariantReflection, KotlinVariantData> {
    override fun buildComponent(origin: KotlinVariantReflection, importingContext: KotlinProjectModelImportingContext): KotlinVariantData? {
        //TODO if context contains fragment with the same [name + KotlinModuleIdentifier.toString] then just populate attributes
        val fragmentStub = KotlinGradleFragmentProtoBuilder.buildComponent(origin, importingContext) ?: return null
        val kotlinCompilationOutput = origin.compilationOutputs?.let { KotlinCompilationOutputBuilder.buildComponent(it) }

        return KotlinGradleVariantData(
            variantAttributes = origin.variantAttributes ?: return null,
            compilationOutputs = kotlinCompilationOutput
        ).also { importingContext.initializeVariantStub(fragmentStub, it) }
    }
}
