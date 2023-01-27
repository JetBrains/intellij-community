// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder

import com.intellij.openapi.observable.properties.ObservableProperty
import org.jetbrains.annotations.ApiStatus

@ApiStatus.ScheduledForRemoval
@Deprecated("Not needed")
interface PlaceholderGroup<K> : RowsRange {

  fun component(key: K, init: Panel.() -> Unit)

  fun setSelectedComponent(key: K)

  fun bindSelectedComponent(property: ObservableProperty<K>): PlaceholderGroup<K>
}