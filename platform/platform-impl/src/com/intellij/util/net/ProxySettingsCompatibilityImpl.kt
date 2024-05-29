// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net

import com.intellij.util.net.internal.asProxySettings

/**
 * Currently delegates to [HttpConfigurable].
 * After [HttpConfigurable] is made internal, we can either drop it and implement a new PSC with migration from previous storage,
 * or continue using [HttpConfigurable] but now being able to modify it.
 */
@Suppress("removal", "DEPRECATION")
internal class ProxySettingsCompatibilityImpl : ProxySettings by (HttpConfigurable::getInstance).asProxySettings(), ProxyConfigurationProvider
