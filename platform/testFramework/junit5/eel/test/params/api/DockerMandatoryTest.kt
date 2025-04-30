// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.params.api

import org.jetbrains.annotations.TestOnly

const val DEFAULT_EEL_TEST_DOCKER_IMAGE: String = "debian"

/**
 * Tests marked with this annotation require docker and will fail without it.
 * You might provide docker [image] name.
 */
@TestOnly
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@TestApplicationWithEel
annotation class DockerMandatoryTest(val image: String = DEFAULT_EEL_TEST_DOCKER_IMAGE)