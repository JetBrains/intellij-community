// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.analysisApi.tests.components.javaInteroperability

import com.intellij.openapi.application.ReadAction
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.util.application
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinProjectStructureProvider
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.name.ClassId

class KtSymbolFromIndexProviderTest : JavaCodeInsightFixtureTestCase() {

    override fun runInDispatchThread() = false

    fun testExtensionFunctionFromJavaFile() {
        application.invokeAndWait {
            myFixture.addFileToProject("com/example/app/util.kt", """
            package com.example.app
            
            class User
            
            fun User.doSomething() {}
            
            """.trimIndent())

            val javaFile = myFixture.addFileToProject("com/example/app/Worker.java", """
            package com.example.app;
            
            public class Worker {
                public void foo() {
                    User user = new User();
                }
            }
            """.trimIndent())

            myFixture.openFileInEditor(javaFile.virtualFile)
        }

        val fqname = ReadAction.compute<String, Exception> {
            val projectStructureProvider = project.getService(KotlinProjectStructureProvider::class.java)!!
            val kaModule = projectStructureProvider.getModule(myFixture.file, null)
            analyze(kaModule) {
                val user = findClass(ClassId.fromString("com/example/app/User"))!!
                val index = KtSymbolFromIndexProvider(null)

                val callables = index.getExtensionCallableSymbolsByNameFilter(
                    nameFilter = { it.asString() == "doSomething" },
                    receiverTypes = listOf(user.defaultType),
                ).toList()

                assertSize(1, callables)

                callables[0].callableId!!.asSingleFqName().toString()
            }
        }

        assertEquals("com.example.app.doSomething", fqname)
    }
}