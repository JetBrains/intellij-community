// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.testGenerator.generator.methods

import com.intellij.openapi.util.io.systemIndependentPath
import org.jetbrains.kotlin.idea.artifacts.AdditionalKotlinArtifacts
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.kotlin.testGenerator.generator.Code
import org.jetbrains.kotlin.testGenerator.generator.TestMethod
import org.jetbrains.kotlin.testGenerator.generator.appendAnnotation
import org.jetbrains.kotlin.testGenerator.generator.appendBlock
import org.jetbrains.kotlin.testGenerator.model.TAnnotation
import org.jetbrains.kotlin.testGenerator.model.makeJavaIdentifier
import java.io.File

class TestCaseMethod(
    private val methodNameBase: String,
    private val contentRootPath: String,
    private val localPath: String,
    private val isCompilerTestData: Boolean,
) : TestMethod {
    override val methodName = run {
        "test" + when (val qualifier = File(localPath).parentFile?.systemIndependentPath ?: "") {
            "" -> methodNameBase
            else -> makeJavaIdentifier(qualifier).capitalize() + "_" + methodNameBase
        }
    }

    fun embed(path: String): TestCaseMethod {
        return TestCaseMethod(methodNameBase, contentRootPath, File(path, localPath).systemIndependentPath, isCompilerTestData)
    }

    override fun Code.render() {
        appendAnnotation(TAnnotation<TestMetadata>(localPath))
        appendBlock("public void $methodName() throws Exception") {
            if (isCompilerTestData) {
                val path = contentRootPath.substringAfter(AdditionalKotlinArtifacts.compilerTestDataDir.name + "/")
                append("runTest(${AdditionalKotlinArtifacts::compilerTestData.name}(\"$path\"));")
            } else {
                append("runTest(\"$contentRootPath\");")
            }
        }
    }
}