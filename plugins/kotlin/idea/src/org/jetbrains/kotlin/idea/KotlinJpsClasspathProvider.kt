package org.jetbrains.kotlin.idea

import com.intellij.compiler.server.BuildProcessParametersProvider
import com.intellij.openapi.application.PathManager.getJarPathForClass

class KotlinJpsClasspathProvider : BuildProcessParametersProvider() {
    override fun getClassPath(): List<String?> {
        return listOf(
            // kotlin-reflect.jar
            getJarPathForClass(kotlin.reflect.full.IllegalCallableAccessException::class.java),

            // kotlin-plugin.jar (aka kotlin-compiler-for-ide.jar)
            getJarPathForClass(org.jetbrains.kotlin.idea.KotlinFileType::class.java),
        )
    }
}