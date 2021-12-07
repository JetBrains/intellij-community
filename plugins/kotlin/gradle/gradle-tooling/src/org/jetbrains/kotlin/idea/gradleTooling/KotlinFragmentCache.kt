// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling

import org.jetbrains.kotlin.idea.projectModel.KotlinFragment
import org.jetbrains.kotlin.idea.projectModel.KotlinModuleIdentifier

interface KotlinFragmentCache {
    fun withCache(
        kotlinModuleIdentifier: KotlinModuleIdentifier, fragmentName: String, createFragment: () -> KotlinFragment
    ): KotlinFragment

    object None : KotlinFragmentCache {
        override fun withCache(
            kotlinModuleIdentifier: KotlinModuleIdentifier,
            fragmentName: String,
            createFragment: () -> KotlinFragment
        ): KotlinFragment = createFragment()
    }
}

internal class DefaultKotlinFragmentCache : KotlinFragmentCache {
    private data class Key(val moduleIdentifier: KotlinModuleIdentifier, val fragmentName: String)

    private val fragments = mutableMapOf<Key, KotlinFragment>()

    override fun withCache(
        kotlinModuleIdentifier: KotlinModuleIdentifier,
        fragmentName: String,
        createFragment: () -> KotlinFragment
    ) = fragments.getOrPut(Key(kotlinModuleIdentifier, fragmentName), createFragment)
}
