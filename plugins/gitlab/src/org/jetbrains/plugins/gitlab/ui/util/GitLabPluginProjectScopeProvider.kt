// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.util

import com.intellij.collaboration.async.PluginScopeProviderBase
import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
internal class GitLabPluginProjectScopeProvider(parentCs: CoroutineScope) : PluginScopeProviderBase(parentCs)