// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import org.jetbrains.kotlin.psi.KtElement

internal interface ImportContext {
    val position: KtElement
    val positionTypeAndReceiver: ImportPositionTypeAndReceiver<*, *>
    val positionType: ImportPositionType get() = positionTypeAndReceiver.positionType
}

internal class DefaultImportContext(
    override val position: KtElement,
    override val positionTypeAndReceiver: ImportPositionTypeAndReceiver<*, *>,
) : ImportContext
