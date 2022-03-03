// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.builders

import org.jetbrains.kotlin.idea.gradleTooling.KotlinModuleImpl
import org.jetbrains.kotlin.idea.gradleTooling.KotlinProjectModelImportingContext
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinModuleReflection
import org.jetbrains.kotlin.idea.projectModel.KotlinModule

object KotlinModuleBuilder : KotlinProjectModelComponentBuilder<KotlinModuleReflection, KotlinModule> {
    override fun buildComponent(origin: KotlinModuleReflection, importingContext: KotlinProjectModelImportingContext): KotlinModule? {
        return KotlinModuleImpl(
            moduleIdentifier = KotlinModuleIdentifierBuilder.buildComponent(origin.moduleIdentifier ?: return null) ?: return null,
            fragments = origin.fragments.orEmpty().mapNotNull { fragmentReflection ->
                KotlinGradleFragmentBuilder.buildComponent(fragmentReflection, importingContext)
            }
        )
    }
}
