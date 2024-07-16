// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight.copyPaste

import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.NotNullableUserDataProperty

/** Test hooks allowing inspection of references restored by KotlinCopyPasteReferenceProcessor. **/
@ApiStatus.Internal
object KotlinCopyPasteActionInfo {
    @get:TestOnly
    var KtFile.importsToBeReviewed: Collection<String> by NotNullableUserDataProperty(
        Key("KOTLIN_COPY_PASTE_IMPORTS_TO_BE_REVIEWED"),
        defaultValue = emptyList()
    )

    @set:TestOnly
    var KtFile.importsToBeDeleted: Collection<String> by NotNullableUserDataProperty(
        Key("KOTLIN_COPY_PASTE_IMPORTS_TO_BE_DELETED"),
        defaultValue = emptyList()
    )

    @get:TestOnly
    var KtFile.declarationsSuggestedToBeImported: Collection<String> by NotNullableUserDataProperty(
        Key("KOTLIN_COPY_PASTE_DECLARATIONS_SUGGESTED_TO_BE_IMPORTED"),
        defaultValue = emptyList()
    )
}