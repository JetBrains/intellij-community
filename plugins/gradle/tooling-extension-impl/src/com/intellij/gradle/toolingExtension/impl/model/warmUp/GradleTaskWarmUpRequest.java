// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.warmUp;

import org.jetbrains.annotations.ApiStatus;

import java.io.Serializable;

/**
 * Marker interface to request for warming-up task configurations.
 *
 * @see com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase#WARM_UP_PHASE
 */
@ApiStatus.Internal
public interface GradleTaskWarmUpRequest extends Serializable {
}
