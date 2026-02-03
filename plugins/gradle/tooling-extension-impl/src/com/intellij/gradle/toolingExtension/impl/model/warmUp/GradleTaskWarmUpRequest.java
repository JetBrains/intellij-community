// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.warmUp;

import org.jetbrains.annotations.ApiStatus;

import java.io.Serializable;

/**
 * Marker interface to request for warming-up task configurations.
 * It evaluates all lazy task configurations that may modify a Gradle project model which is necessary for the following model builders.
 * Also, warmed tasks don't throw configuration exceptions during {@link org.gradle.api.Project#getTasks}
 */
@ApiStatus.Internal
public interface GradleTaskWarmUpRequest extends Serializable {
}
