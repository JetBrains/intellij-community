// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.completion.impl.k2.checkers

import org.jetbrains.kotlin.analysis.api.lifetime.KaLifetimeOwner
import org.jetbrains.kotlin.analysis.api.lifetime.KaLifetimeToken
import org.jetbrains.kotlin.analysis.api.lifetime.withValidityAssertion
import org.jetbrains.kotlin.analysis.api.signatures.KaCallableSignature
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionOptions

internal data class ApplicableExtension(
    private val _signature: KaCallableSignature<*>,
    val insertionOptions: CallableInsertionOptions,
): KaLifetimeOwner {

    override val token: KaLifetimeToken get() = _signature.token

    val signature: KaCallableSignature<*> = withValidityAssertion { _signature }
}