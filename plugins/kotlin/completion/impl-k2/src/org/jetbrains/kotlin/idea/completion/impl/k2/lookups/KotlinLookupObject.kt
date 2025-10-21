// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.lookups

import kotlinx.serialization.Polymorphic
import org.jetbrains.kotlin.idea.completion.api.serialization.SerializableLookupObject
import org.jetbrains.kotlin.name.Name

@Polymorphic
internal interface KotlinLookupObject: SerializableLookupObject {
    val shortName: Name
}