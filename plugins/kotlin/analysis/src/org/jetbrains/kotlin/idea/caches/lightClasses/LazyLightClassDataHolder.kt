// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.caches.lightClasses

import org.jetbrains.kotlin.asJava.LightClassBuilder
import org.jetbrains.kotlin.asJava.builder.LightClassBuilderResult
import org.jetbrains.kotlin.asJava.builder.LightClassConstructionContext
import org.jetbrains.kotlin.asJava.builder.LightClassDataHolder
import org.jetbrains.kotlin.asJava.classes.lazyPub
import org.jetbrains.kotlin.resolve.diagnostics.Diagnostics
import org.jetbrains.kotlin.storage.StorageManager

typealias ExactLightClassContextProvider = () -> LightClassConstructionContext
typealias DiagnosticsHolderProvider = () -> LazyLightClassDataHolder.DiagnosticsHolder

class LazyLightClassDataHolder(
    private val builder: LightClassBuilder,
    private val exactContextProvider: ExactLightClassContextProvider,
    private val diagnosticsHolderProvider: DiagnosticsHolderProvider
) : LightClassDataHolder.ForClass, LightClassDataHolder.ForFacade, LightClassDataHolder.ForScript {

    class DiagnosticsHolder(storageManager: StorageManager) {
        private val cache = storageManager.createCacheWithNotNullValues<LazyLightClassDataHolder, Diagnostics>()

        fun getOrCompute(lazyLightClassDataHolder: LazyLightClassDataHolder, diagnostics: () -> Diagnostics) =
            cache.computeIfAbsent(lazyLightClassDataHolder, diagnostics)
    }

    private val _builderExactContextProvider: LightClassBuilderResult by lazyPub { builder(exactContextProvider()) }

    private val exactResultLazyValue = lazyPub { _builderExactContextProvider.stub }

    override val javaFileStub by exactResultLazyValue

    override val extraDiagnostics: Diagnostics
        get() = diagnosticsHolderProvider().getOrCompute(this) {
            // Force lazy diagnostics computation because otherwise a lot of memory is retained by computation.
            // NB: Laziness here is not crucial anyway since somebody already has requested diagnostics and we hope one will use them
            _builderExactContextProvider.diagnostics.takeUnless { it.isEmpty() } ?: Diagnostics.EMPTY
        }
}
