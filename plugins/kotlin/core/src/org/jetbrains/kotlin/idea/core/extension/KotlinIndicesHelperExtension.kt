// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core.extension

import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.extensions.ProjectExtensionDescriptor
import org.jetbrains.kotlin.incremental.components.LookupLocation
import org.jetbrains.kotlin.types.KotlinType

interface KotlinIndicesHelperExtension {
    companion object : ProjectExtensionDescriptor<KotlinIndicesHelperExtension>(
        "org.jetbrains.kotlin.kotlinIndicesHelperExtension", KotlinIndicesHelperExtension::class.java
    )

    @JvmDefault
    fun appendExtensionCallables(
        consumer: MutableList<in CallableDescriptor>,
        moduleDescriptor: ModuleDescriptor,
        receiverTypes: Collection<KotlinType>,
        nameFilter: (String) -> Boolean,
        lookupLocation: LookupLocation
    ) {
        appendExtensionCallables(consumer, moduleDescriptor, receiverTypes, nameFilter)
    }

    @Deprecated(
        "Override the appendExtensionCallables() with the 'lookupLocation' parameter instead. " +
                "This method can then throw an exception. " +
                "See 'DebuggerFieldKotlinIndicesHelperExtension' as an example."
    )
    fun appendExtensionCallables(
        consumer: MutableList<in CallableDescriptor>,
        moduleDescriptor: ModuleDescriptor,
        receiverTypes: Collection<KotlinType>,
        nameFilter: (String) -> Boolean
    )
}