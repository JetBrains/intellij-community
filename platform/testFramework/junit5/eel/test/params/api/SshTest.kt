// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.params.api

import org.jetbrains.annotations.TestOnly

/**
 * Runs the test on IJent over OpenSSH through the JetBrains daemon, in a Docker container with sshd.
 *
 * The `ssh` label of [com.intellij.testFramework.junit5.eel.EelFixtureFilter] turns this environment on.
 * A test marked with [mandatory] fails when the environment is not available.
 * The provider is in `intellij.platform.ijent.ssh.testFramework.tests`. Without that module on the classpath, no test
 * gets this environment.
 *
 * The framework reads the annotations of a provider as repeatable. Each [SshTest] gives one SSH environment.
 */
@TestOnly
@Repeatable
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@TestApplicationWithEel
annotation class SshTest(val mandatory: Boolean = true)
