/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.builders

import org.jetbrains.kotlin.gradle.KotlinKPMModule
import org.jetbrains.kotlin.gradle.KotlinModuleImpl
import org.jetbrains.kotlin.gradle.KotlinProjectModelImportingContext
import org.jetbrains.kotlin.reflect.KotlinModuleReflection

object KotlinModuleBuilder : KotlinProjectModelComponentBuilder<KotlinModuleReflection, KotlinKPMModule> {
    override fun buildComponent(origin: KotlinModuleReflection, importingContext: KotlinProjectModelImportingContext): KotlinKPMModule? {
        return KotlinModuleImpl(
            moduleIdentifier = KotlinModuleIdentifierBuilder.buildComponent(origin.moduleIdentifier ?: return null) ?: return null,
            fragments = origin.fragments.orEmpty().mapNotNull { fragmentReflection ->
                KotlinGradleFragmentBuilder.buildComponent(fragmentReflection, importingContext)
            }
        )
    }
}
