// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.testGenerator.model

import org.jetbrains.kotlin.idea.artifacts.AdditionalKotlinArtifacts
import org.jetbrains.kotlin.test.KotlinRoot
import java.io.File

interface TGroup {
    val modulePath: String

    val testSourcesPath: String
    val testDataPath: String

    val kotlinRoot: File
    val moduleRoot: File
    val testSourcesRoot: File
    val testDataRoot: File

    val suites: List<TSuite>

    val isCompilerTestData: Boolean
}

interface MutableTGroup : TGroup {
    override val suites: MutableList<TSuite>
}

class TGroupImpl(
    override val modulePath: String,
    override val testSourcesPath: String,
    override val testDataPath: String,
    override val isCompilerTestData: Boolean,
) : MutableTGroup {
    override val kotlinRoot = KotlinRoot.DIR
    override val moduleRoot = File(kotlinRoot, modulePath)
    override val testSourcesRoot = File(moduleRoot, testSourcesPath)
    override val testDataRoot = if (testDataPath.startsWith("/")) File(testDataPath) else File(moduleRoot, testDataPath)
    override val suites = mutableListOf<TSuite>()
}

fun MutableTWorkspace.testGroup(
    modulePath: String,
    testSourcesPath: String = "test",
    testDataPath: String = "testData",
    block: MutableTGroup.() -> Unit
) {
    groups += TGroupImpl(
        modulePath,
        testSourcesPath,
        testDataPath,
        isCompilerTestData = testDataPath.startsWith(AdditionalKotlinArtifacts.compilerTestDataDir.canonicalPath)
    ).apply(block)
}