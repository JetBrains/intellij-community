// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.sourceSetArtifactIndex;

import org.jetbrains.annotations.ApiStatus;

/**
 * Marker interface to request for building artifact index {@link GradleSourceSetArtifactIndex}.
 * This index is available only on the Gradle side and cannot be transferred to the IDE.
 *
 * @see GradleSourceSetArtifactIndex
 */
@ApiStatus.Internal
public interface GradleSourceSetArtifactBuildRequest {
}
