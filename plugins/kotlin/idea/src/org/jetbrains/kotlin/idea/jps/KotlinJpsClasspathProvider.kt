// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.jps

import com.intellij.compiler.server.BuildProcessParametersProvider
import com.intellij.openapi.application.PathManager
import org.jetbrains.kotlin.idea.KotlinFileType
import kotlin.reflect.full.IllegalCallableAccessException

/**
 * Test - [org.jetbrains.kotlin.idea.KotlinJpsClasspathProviderTest]
 */
class KotlinJpsClasspathProvider : BuildProcessParametersProvider() {
    override fun getClassPath(): List<String?> {
        return listOf(
            // kotlin-reflect.jar
            PathManager.getJarPathForClass(IllegalCallableAccessException::class.java),

            // kotlin-plugin.jar (aka kotlin-compiler-for-ide.jar)
            // TODO: note it has to be compiler-components-for-jps.jar rather than kotlin-compiler-for-ide.jar
            //  as kotlin-compiler-for-ide.jar includes kotlin-jps-common.jar as well (+maybe smth else)
            PathManager.getJarPathForClass(KotlinFileType::class.java),
        )
    }
}