// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion.test

import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import java.io.File

fun CodeInsightTestFixture.configureWithExtraFile(path: String, vararg extraNameParts: String = arrayOf(".Data")) {
    val fileName = File(path).name

    val noExtensionPath = FileUtil.getNameWithoutExtension(fileName)
    val extensions = arrayOf("kt", "java", "properties")
    val extraPaths: List<String> = extraNameParts.flatMap { extensions.map { ext -> "$noExtensionPath$it.$ext" } }
        .mapNotNull { File(testDataPath, it).takeIf { file -> file.exists() }?.name }

    configureByFiles(*(listOf(fileName) + extraPaths).toTypedArray())
}

inline fun <reified T : Any> Any?.assertInstanceOf() = UsefulTestCase.assertInstanceOf(this, T::class.java)

inline fun <reified T : Any, R> ComponentManager.withServiceRegistered(instance: T, body: () -> R): R {
    val picoContainer = this as ComponentManagerImpl
    val key = T::class.java.name
    try {
        picoContainer.unregisterComponent(key)
        picoContainer.registerComponentInstance(key, instance)
        return body()
    } finally {
        picoContainer.unregisterComponent(key)
    }
}
