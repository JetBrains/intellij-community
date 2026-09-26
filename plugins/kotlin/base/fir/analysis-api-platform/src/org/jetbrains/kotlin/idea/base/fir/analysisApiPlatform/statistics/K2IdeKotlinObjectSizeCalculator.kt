// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.statistics

import com.intellij.util.containers.ContainerUtil
import org.ehcache.sizeof.SizeOf
import org.jetbrains.kotlin.analysis.api.platform.statistics.KotlinObjectSizeCalculator
import kotlin.reflect.KClass

/**
 * The IDE-side implementation allows us to limit the dependency on the SizeOf library to IntelliJ (so we don't need this dependency in the
 * Kotlin repository). This makes sense as the user of the platform component (session structure logging) is only intended to be executed
 * from IntelliJ for now.
 *
 * The calculator should not be used in production. The usage of [SizeOf] attaches a dynamic agent, which is undesirable in production.
 */
internal class K2IdeKotlinObjectSizeCalculator : KotlinObjectSizeCalculator {
    /**
     * The cache has a weak key to avoid potential class loader memory leaks.
     */
    private val cache = ContainerUtil.createConcurrentWeakMap<KClass<*>, Long>()

    override fun shallowSize(value: Any): Long =
        cache.computeIfAbsent(value::class) { kClass ->
            val javaClass = kClass.java
            if (javaClass.isArray || javaClass.isPrimitive) {
                error("Shallow size should not be naively calculated for arrays or primitives: '$value'.")
            }

            SizeOf.newInstance().sizeOf(value)
        }
}
