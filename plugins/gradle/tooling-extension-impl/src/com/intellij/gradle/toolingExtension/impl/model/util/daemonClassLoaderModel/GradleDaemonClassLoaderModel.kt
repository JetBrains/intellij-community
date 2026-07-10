// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.util.daemonClassLoaderModel

import org.jetbrains.annotations.ApiStatus.Internal
import java.io.Serializable

/**
 * This model is used for resolving Gradle Daemon [ClassLoader]
 *
 * @see com.intellij.gradle.toolingExtension.impl.util.GradleClassLoaderUtil.getDaemonClassLoader
 */
@Internal
interface GradleDaemonClassLoaderModel : Serializable
