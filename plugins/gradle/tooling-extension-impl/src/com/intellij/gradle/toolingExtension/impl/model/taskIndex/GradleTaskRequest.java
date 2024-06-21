// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.taskIndex;

import org.jetbrains.annotations.ApiStatus;

import java.io.Serializable;

/**
 * Marker interface to request for building task index {@link GradleTaskIndex}.
 * This index is available only on the Gradle side and cannot be transferred to the IDE.
 *
 * @see GradleTaskIndex
 */
@ApiStatus.Internal
public interface GradleTaskRequest extends Serializable {
}
