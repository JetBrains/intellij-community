// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea

import com.intellij.DynamicBundle
import com.intellij.compiler.server.BuildProcessParametersProvider
import com.intellij.openapi.application.PathManager.getJarPathForClass

class KotlinJpsClasspathProvider : BuildProcessParametersProvider() {
    override fun getClassPath(): List<String?> {
        return listOf(
            // kotlin-reflect.jar
            getJarPathForClass(kotlin.reflect.full.IllegalCallableAccessException::class.java),

            // kotlin-plugin.jar (aka kotlin-compiler-for-ide.jar)
            // TODO: note it has to be compiler-components-for-jps.jar rather than kotlin-compiler-for-ide.jar
            //  as kotlin-compiler-for-ide.jar includes kotlin-jps-common.jar as well (+maybe smth else)
            // kotlin-compiler-common-for-ide.jar
            getJarPathForClass(org.jetbrains.kotlin.idea.KotlinFileType::class.java),

            // kotlin-compiler-fe10-for-ide.jar
            getJarPathForClass(org.jetbrains.kotlin.descriptors.ClassDescriptor::class.java),

            // kotlin-compiler-ir-for-ide.jar
            getJarPathForClass(org.jetbrains.kotlin.ir.IrElement::class.java),

            // Base of i18n
            getJarPathForClass(DynamicBundle::class.java),
        )
    }
}