// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization

import org.jetbrains.kotlin.ObsoleteTestInfrastructure
import org.jetbrains.kotlin.checkers.AbstractDiagnosticsTest
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.config.JvmClasspathRoot
import org.jetbrains.kotlin.extensions.StorageComponentContainerContributor
import org.jetbrains.kotlinx.serialization.compiler.extensions.SerializationIDEContainerContributor

@OptIn(ObsoleteTestInfrastructure::class)
abstract class AbstractSerializationPluginIdeDiagnosticTest : AbstractDiagnosticsTest() {
    private val coreLibraryPath = getSerializationCoreLibraryJar()!!
    private val jsonLibraryPath = getSerializationJsonLibraryJar()!!

    override fun setupEnvironment(environment: KotlinCoreEnvironment) {
        if (!StorageComponentContainerContributor.getInstances(project).any { it is SerializationIDEContainerContributor }) {
            StorageComponentContainerContributor.registerExtension(project, SerializationIDEContainerContributor())
        }
        environment.updateClasspath(listOf(JvmClasspathRoot(coreLibraryPath), JvmClasspathRoot(jsonLibraryPath)))
    }
}
