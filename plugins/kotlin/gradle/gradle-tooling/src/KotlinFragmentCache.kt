/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.gradle

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
