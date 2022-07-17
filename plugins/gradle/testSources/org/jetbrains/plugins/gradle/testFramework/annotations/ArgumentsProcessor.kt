// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.annotations

import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.support.AnnotationConsumer

/**
 * Describes JUnit5 test source annotation processor.
 * It processes and provides data from source annotation into test parameters.
 *
 * @see ArgumentsProvider
 * @see AnnotationConsumer
 */
interface ArgumentsProcessor<T : Annotation> : ArgumentsProvider, AnnotationConsumer<T>
