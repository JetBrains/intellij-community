// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea

import com.intellij.compiler.server.BuildProcessParametersProvider
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout

class KotlinJpsClasspathProvider : BuildProcessParametersProvider() {
    override fun getClassPath(): List<String> {
        return listOf(KotlinPluginLayout.instance.jpsPluginJar.canonicalPath)
    }
}