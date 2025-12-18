// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.impl.legacy

import com.intellij.platform.projectView.pane.ProjectViewPaneProviderId
import com.intellij.platform.projectView.pane.projectViewPaneProviderId
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
val LEGACY_PROVIDER_ID: ProjectViewPaneProviderId = projectViewPaneProviderId("legacy")
