// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.collectReceiverTypesForElement
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression

internal interface ImportContext {
    val position: KtElement
    val positionType: ImportPositionType
    val isExplicitReceiver: Boolean

    context(KaSession)
    fun receiverTypes(): List<KaType>
}

internal class DefaultImportContext(
    override val position: KtElement,
    private val positionTypeAndReceiver: ImportPositionTypeAndReceiver<*, *>,
) : ImportContext {
    override val positionType: ImportPositionType get() = positionTypeAndReceiver.positionType
    override val isExplicitReceiver: Boolean get() = positionTypeAndReceiver.receiver != null

    context(KaSession)
    override fun receiverTypes(): List<KaType> =
        collectReceiverTypesForElement(position, positionTypeAndReceiver.receiver as? KtExpression)
}
