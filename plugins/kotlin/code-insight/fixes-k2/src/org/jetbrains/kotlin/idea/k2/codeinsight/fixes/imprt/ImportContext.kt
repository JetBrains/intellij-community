// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.lifetime.KaLifetimeOwner
import org.jetbrains.kotlin.analysis.api.lifetime.KaLifetimeToken
import org.jetbrains.kotlin.analysis.api.lifetime.withValidityAssertion
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.collectReceiverTypesForElement
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression

internal interface ImportContext {
    val position: KtElement
    val positionType: ImportPositionType

    /**
     * Indicates whether the receiver of a call is explicitly applied in the position
     * represented by this import context.
     *
     * Note: If [isExplicitReceiver] is `true`, it does not mean that there
     * is an explicit PSI expression which can be considered a receiver.
     * It just means that the type of explicit receiver cannot be ignored
     * when applying auto-import candidates.
     */
    val isExplicitReceiver: Boolean

    context(_: KaSession)
    fun receiverTypes(): List<KaType>
}

internal class DefaultImportContext(
    override val position: KtElement,
    private val positionTypeAndReceiver: ImportPositionTypeAndReceiver<*, *>,
) : ImportContext {
    override val positionType: ImportPositionType get() = positionTypeAndReceiver.positionType
    override val isExplicitReceiver: Boolean get() = positionTypeAndReceiver.receiver != null

    context(_: KaSession)
    override fun receiverTypes(): List<KaType> =
        collectReceiverTypesForElement(position, positionTypeAndReceiver.receiver as? KtExpression)
}

/**
 * An implementation of [ImportContext] with a fixed [KaType] as a receiver type.
 * This is useful when you know the receiver type in advance and don't need to calculate it dynamically.
 */
internal class ImportContextWithFixedReceiverType(
    override val position: KtElement,
    override val positionType: ImportPositionType,
    private val explicitReceiverType: KaType,
) : ImportContext, KaLifetimeOwner {
    override val token: KaLifetimeToken get() = explicitReceiverType.token

    override val isExplicitReceiver: Boolean = true

    context(_: KaSession)
    override fun receiverTypes(): List<KaType> = withValidityAssertion {
        listOf(explicitReceiverType)
    }
}
