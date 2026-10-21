// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.impl

import kotlinx.coroutines.CoroutineScope

/**
 * Gives the scope of the Search Everywhere sessions of one client project session.
 *
 * The scope ends when the client session ends, for example on the final disconnect of the client.
 * The scope also ends on the project close or the plugin unload.
 * An RPC reconnect keeps the client session, so it keeps the scope.
 */
internal class SeClientSessionScope(val coroutineScope: CoroutineScope)
