// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl.providers

import com.intellij.platform.settings.v2.LayerProvider

/**
 * Returns the registered provider of [providerClass], or `null` when it is not registered now. A backing service
 * resolves its stateless, singleton provider this way to tag the [LayerData][com.intellij.platform.settings.v2.LayerData]
 * that it gives to the vault. So the service holds no reference to the provider.
 *
 * It picks the narrowest match first, the reverse of the EP order. The vault targets a write to the same instance.
 * Normally a class has one instance, so the order does not matter. But a test can register a second one on top of
 * the platform provider. Then the tag must match the write target, or the pushed and loaded data land under one
 * instance while the writes go to the other.
 */
internal fun layerProvider(providerClass: Class<out LayerProvider>): LayerProvider? =
  LayerProvider.EP_NAME.extensionList.asReversed().firstOrNull(providerClass::isInstance)
