// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.lookups

internal abstract class KotlinCallableLookupObject : KotlinLookupObject {
    abstract val renderedDeclaration: String
    abstract val options: CallableInsertionOptions
}