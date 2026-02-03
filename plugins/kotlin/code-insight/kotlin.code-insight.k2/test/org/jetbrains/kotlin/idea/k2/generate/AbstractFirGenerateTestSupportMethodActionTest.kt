// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.generate
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_MODULE_ENTITY_TYPE_ID_NAME
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.codeInsight.generate.AbstractCodeInsightActionTest
import org.jetbrains.kotlin.idea.k2.codeinsight.generate.KotlinGenerateTestSupportActionBase

abstract class AbstractFirGenerateTestSupportMethodActionTest : AbstractCodeInsightActionTest() {

    override fun createAction(fileText: String): KotlinGenerateTestSupportActionBase {
        val actionClassName = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// K2_ACTION_CLASS: ")
        val action = Class.forName(actionClassName).getDeclaredConstructor().newInstance() as KotlinGenerateTestSupportActionBase
        action.testFrameworkToUse = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// TEST_FRAMEWORK:")
        return action
    }

    override fun getProjectDescriptor(): LightProjectDescriptor = TEST_ROOT_PROJECT_DESCRIPTOR

    companion object {
        val TEST_ROOT_PROJECT_DESCRIPTOR = object : LightProjectDescriptor() {
            override fun getModuleTypeId(): String = JAVA_MODULE_ENTITY_TYPE_ID_NAME
            override fun getSdk(): Sdk = IdeaTestUtil.getMockJdk18()
            override fun getSourceRootType(): JpsModuleSourceRootType<*> = JavaSourceRootType.TEST_SOURCE
        }
    }
}