// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.caches.project

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.UserDataHolder
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import kotlin.reflect.KProperty

fun <T> Module.cacheByClass(classForKey: Class<*>, vararg dependencies: Any, provider: () -> T): T {
    return CachedValuesManager.getManager(project).cache(this, dependencies, classForKey, provider)
}

@Deprecated("consider to use WorkspaceModelChangeListener")
fun <T> Module.cacheByClassInvalidatingOnRootModifications(classForKey: Class<*>, provider: () -> T): T {
    return cacheByClass(classForKey, ProjectRootModificationTracker.getInstance(project), provider = provider)
}

/**
 * Note that it uses lambda's class for caching (essentially, anonymous class), which means that all invocations will be cached
 * by the one and the same key.
 * It is encouraged to use explicit class, just for the sake of readability.
 */
@Deprecated("consider to use WorkspaceModelChangeListener")
fun <T> Module.cacheInvalidatingOnRootModifications(provider: () -> T): T {
    return cacheByClassInvalidatingOnRootModifications(provider::class.java, provider)
}

fun <T> Project.cacheByClass(classForKey: Class<*>, vararg dependencies: Any, provider: () -> T): T {
    return CachedValuesManager.getManager(this).cache(this, dependencies, classForKey, provider)
}

@Deprecated("consider to use WorkspaceModelChangeListener")
fun <T> Project.cacheByClassInvalidatingOnRootModifications(classForKey: Class<*>, provider: () -> T): T {
    return cacheByClass(classForKey, ProjectRootModificationTracker.getInstance(this), provider = provider)
}

/**
 * Note that it uses lambda's class for caching (essentially, anonymous class), which means that all invocations will be cached
 * by the one and the same key.
 * It is encouraged to use explicit class, just for the sake of readability.
 */
@Suppress("DEPRECATION")
@Deprecated("consider to use WorkspaceModelChangeListener")
fun <T> Project.cacheInvalidatingOnRootModifications(provider: () -> T): T {
    return cacheByClassInvalidatingOnRootModifications(provider::class.java, provider)
}

private fun <T> CachedValuesManager.cache(
    holder: UserDataHolder,
    dependencies: Array<out Any>,
    classForKey: Class<*>,
    provider: () -> T
): T {
    return getCachedValue(
        holder,
        getKeyForClass(classForKey),
        { CachedValueProvider.Result.create(provider(), *dependencies) },
        false
    )
}

operator fun <T> CachedValue<T>.getValue(o: Any, property: KProperty<*>): T = value

fun <T> CachedValue(project: Project, trackValue: Boolean = false, provider: () -> CachedValueProvider.Result<T>) =
    CachedValuesManager.getManager(project).createCachedValue(provider, trackValue)
