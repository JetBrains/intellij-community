// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncContributor.bridge

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.syncAction.GradleEntitySource

/**
 * The Gradle workspace entity source, for temporary (preview) entities
 * that should be deleted at the beginning of the data services phase.
 *
 * Marked temporary entities should have data service that will return analogise entities into workspace model.
 * For example, all module entities already have module and source set data services
 * that import corresponding entities using the bridge WSM API.
 */
@ApiStatus.Internal
interface GradleBridgeEntitySource : GradleEntitySource