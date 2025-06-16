// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.indices

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.annotations.ApiStatus
import kotlin.time.*

@ApiStatus.Internal
inline fun getByKeyMaxDuration(): Duration =
    Registry.intValue("kotlin.indices.timing.threshold.single", Int.MAX_VALUE).toDuration(DurationUnit.MILLISECONDS)

@ApiStatus.Internal
inline fun processElementsMaxDuration(): Duration =
    Registry.intValue("kotlin.indices.timing.threshold.batch", Int.MAX_VALUE).toDuration(DurationUnit.MILLISECONDS)

@ApiStatus.Internal
inline fun collectionThresholdSize(): Int =
    Registry.intValue("kotlin.indices.keys.threshold.size", Int.MAX_VALUE)

@ApiStatus.Internal
inline fun <T> processElementsAndMeasure(index: StubIndexKey<*, *>, log: Logger, crossinline block: () -> T): T =
    measureIndexCall(
        index,
        "processElements",
        processElementsMaxDuration(),
        log,
        block
    )

@ApiStatus.Internal
inline fun <T> processAllKeysAndMeasure(index: StubIndexKey<*, *>, log: Logger, crossinline block: () -> T): T =
        measureIndexCall(index, "processAllKeys", processElementsMaxDuration(), log, block)

@ApiStatus.Internal
inline fun <T> getByKeyAndMeasure(index: StubIndexKey<*, *>, log: Logger, crossinline block: () -> T): T =
        measureIndexCall(index, "getByKey", getByKeyMaxDuration(), log, block)

@ApiStatus.Internal
inline fun <T> getAllKeysAndMeasure(index: StubIndexKey<*, *>, log: Logger, crossinline block: () -> T): T =
    measureIndexCall(index, "getAllKeys", processElementsMaxDuration(), log, block)

@ApiStatus.Internal
@OptIn(ExperimentalTime::class)
inline fun <T> measureIndexCall(index: StubIndexKey<*, *>, prefix: String, threshold: Duration, log: Logger, crossinline block: () -> T): T {
    val mark = TimeSource.Monotonic.markNow()
    val t = block()
    val elapsed = mark.elapsedNow()
    if (elapsed > threshold) {
        val application = ApplicationManager.getApplication()
        if (application.isInternal && !application.isUnitTestMode && Registry.`is`("kotlin.indices.diagnostic.enabled")) {
            log.error("${index.name} $prefix took $elapsed more than expected $threshold")
        }
    }
    return t
}

@ApiStatus.Internal
@OptIn(ExperimentalTime::class)
inline fun <T> checkCollectionSize(index: StubIndexKey<*, *>, prefix: String, log: Logger, collection: Collection<T>): Collection<T> {
    val thresholdSize = collectionThresholdSize()
    val size = collection.size
    if (size > thresholdSize) {
        val application = ApplicationManager.getApplication()
        if (application.isInternal && !application.isUnitTestMode && Registry.`is`("kotlin.indices.diagnostic.enabled")) {
            log.error("${index.name} $prefix is going to process collection with $size elements more than expected $thresholdSize")
        }
    }
    return collection
}