// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.caches.resolve

import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingTraceFilter
import org.jetbrains.kotlin.resolve.DelegatingBindingTrace
import org.jetbrains.kotlin.util.slicedMap.WritableSlice

class BindingTraceForBodyResolve(
    parentContext: BindingContext,
    debugName: String,
    filter: BindingTraceFilter = BindingTraceFilter.ACCEPT_ALL
) : DelegatingBindingTrace(parentContext, debugName, filter = filter, allowSliceRewrite = true) {
    override fun <K, V> getKeys(slice: WritableSlice<K, V>): Collection<K> {
        if (slice == BindingContext.DEFERRED_TYPE) {
            return map.getKeys(slice)
        }

        return super.getKeys(slice)
    }
}