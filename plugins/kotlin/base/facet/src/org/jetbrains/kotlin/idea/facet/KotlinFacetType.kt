// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.facet

import com.intellij.facet.FacetType
import com.intellij.facet.FacetTypeId
import com.intellij.facet.FacetTypeRegistry
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.kotlin.idea.base.util.KotlinPlatformUtils
import org.jetbrains.kotlin.idea.KotlinIcons
import javax.swing.Icon

abstract class KotlinFacetType<C : KotlinFacetConfiguration> :
    FacetType<KotlinFacet, C>(TYPE_ID, ID, NAME) {
    companion object {
        const val ID = "kotlin-language"
        val TYPE_ID = FacetTypeId<KotlinFacet>(ID)

        @NlsSafe
        const val NAME = "Kotlin"

        val INSTANCE
            get() = FacetTypeRegistry.getInstance().findFacetType(TYPE_ID)
    }

    override fun isSuitableModuleType(moduleType: ModuleType<*>): Boolean {
        return when {
            KotlinPlatformUtils.isCidr -> true
            else -> moduleType is JavaModuleType
        }
    }

    override fun getIcon(): Icon = KotlinIcons.SMALL_LOGO
}
