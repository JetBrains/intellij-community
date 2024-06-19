// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncContributor.entitites

import com.intellij.platform.workspace.storage.EntitySource

/**
 * Any Gradle Entity for Workspace Model inherits this marker interface
 */
interface GradleEntitySource : EntitySource
