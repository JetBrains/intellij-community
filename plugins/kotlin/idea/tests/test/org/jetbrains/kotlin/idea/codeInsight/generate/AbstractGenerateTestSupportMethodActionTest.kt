// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.generate

import com.intellij.openapi.module.ModuleTypeId
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.kotlin.idea.actions.generate.KotlinGenerateTestSupportActionBase
import org.jetbrains.kotlin.idea.test.PluginTestCaseBase
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils

abstract class AbstractGenerateTestSupportMethodActionTest : AbstractCodeInsightActionTest() {
    override fun createAction(fileText: String) = (super.createAction(fileText) as KotlinGenerateTestSupportActionBase).apply {
        testFrameworkToUse = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// TEST_FRAMEWORK:")
    }

    override fun getProjectDescriptor(): LightProjectDescriptor = TEST_ROOT_PROJECT_DESCRIPTOR

    companion object {
        val TEST_ROOT_PROJECT_DESCRIPTOR = object : LightProjectDescriptor() {
            override fun getModuleTypeId(): String = ModuleTypeId.JAVA_MODULE
            override fun getSdk(): Sdk = IdeaTestUtil.getMockJdk18()
            override fun getSourceRootType(): JpsModuleSourceRootType<*> = JavaSourceRootType.TEST_SOURCE
        }
    }
}