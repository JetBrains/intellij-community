// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.serialization;

import org.gradle.tooling.model.UnsupportedMethodException;
import org.gradle.tooling.provider.model.ToolingModelBuilder;

/**
 * The service should provide more efficient data serialization of objects created by {@link ToolingModelBuilder} provided by target Gradle build.
 * Gradle supports backward and upward compatibility for tooling models,
 * so the service should be aware of possible {@link UnsupportedMethodException}s caused by the tooling object methods calls.
 * <p>
 *
 * @author Vladislav.Soroka
 *
 * @see ToolingModelBuilder
 */
public interface GradleBuiltinModelSerializationService<T> extends SerializationService<T> {
}
