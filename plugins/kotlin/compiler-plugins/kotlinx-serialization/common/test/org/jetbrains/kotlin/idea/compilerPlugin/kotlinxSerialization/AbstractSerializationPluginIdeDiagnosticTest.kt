// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization

import org.jetbrains.kotlin.ObsoleteTestInfrastructure
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.config.JvmClasspathRoot
import org.jetbrains.kotlin.extensions.StorageComponentContainerContributor
import org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization.compiler.extensions.SerializationIDEContainerContributor
import org.jetbrains.kotlin.idea.test.KotlinBaseTest
import java.io.File

@OptIn(ObsoleteTestInfrastructure::class)
abstract class AbstractSerializationPluginIdeDiagnosticTest : KotlinBaseTest<KotlinBaseTest.TestFile>() {
    private val coreLibraryPath = getSerializationCoreLibraryJar()!!
    private val jsonLibraryPath = getSerializationJsonLibraryJar()!!

    override fun setupEnvironment(environment: KotlinCoreEnvironment) {
        if (!StorageComponentContainerContributor.getInstances(environment.project).any { it is SerializationIDEContainerContributor }) {
            StorageComponentContainerContributor.registerExtension(environment.project, SerializationIDEContainerContributor())
        }
        environment.updateClasspath(listOf(JvmClasspathRoot(coreLibraryPath), JvmClasspathRoot(jsonLibraryPath)))
    }

    override fun createTestFilesFromFile(file: File, expectedText: String): List<TestFile> {
        TODO("Not yet implemented")
    }
}
