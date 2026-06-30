// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.devkit.gradle.tooling.IntelliJPlatformAuxiliaryArtifactProvider
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension

internal class DevKitGradleProjectResolverExtension : AbstractProjectResolverExtension() {
  override fun getToolingExtensionsClasses(): Set<Class<*>> =
    setOf(IntelliJPlatformAuxiliaryArtifactProvider::class.java)
}
