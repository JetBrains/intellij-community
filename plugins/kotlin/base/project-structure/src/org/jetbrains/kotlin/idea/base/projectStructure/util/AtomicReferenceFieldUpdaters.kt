// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.util

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater
import kotlin.reflect.KMutableProperty1

/**
 * A shortcut to create [AtomicReferenceFieldUpdater] based on a Kotlin [property].
 * It's more convenient than using bare-bones [AtomicReferenceFieldUpdater.newUpdater],
 * and leaves less room for errors and typos.
 *
 * [property] should point to a Kotlin property marked with a [Volatile] annotation and with non-primitive [ReturnType].
 */
internal inline fun <reified ReceiverType, reified ReturnType> createAtomicReferenceFieldUpdaterForProperty(
    property: KMutableProperty1<ReceiverType, ReturnType>
): AtomicReferenceFieldUpdater<ReceiverType, ReturnType> =
    AtomicReferenceFieldUpdater.newUpdater(
        ReceiverType::class.java,
        ReturnType::class.java,
        property.name,
    )
