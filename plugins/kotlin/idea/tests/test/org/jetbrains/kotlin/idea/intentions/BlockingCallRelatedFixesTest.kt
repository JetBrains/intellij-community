// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInspection.blockingCallsDetection.BlockingMethodInNonBlockingContextInspection
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.psi.impl.source.tree.injected.changesHandler.range
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.MavenDependencyUtil
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.junit.Assert

private val ktProjectDescriptor = object : KotlinWithJdkAndRuntimeLightProjectDescriptor(
    listOf(TestKotlinArtifacts.kotlinStdlib), listOf(TestKotlinArtifacts.kotlinStdlibSources)
) {
    override fun configureModule(module: Module, model: ModifiableRootModel) {
        super.configureModule(module, model)
        MavenDependencyUtil.addFromMaven(model, "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    }
}

class BlockingCallRelatedFixesTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor {
        return ktProjectDescriptor
    }

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject(
            "org/jetbrains/annotations/Blocking.java",
            """
                package org.jetbrains.annotations; 

                public @interface Blocking {}
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "org/jetbrains/annotations/NonBlocking.java",
            """
                package org.jetbrains.annotations; 

                public @interface NonBlocking {}
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "blockingMethod.kt",
            """
                import org.jetbrains.annotations.Blocking

                @Blocking fun block(): Int { return 42 }
            """.trimIndent()
        )
        myFixture.enableInspections(BlockingMethodInNonBlockingContextInspection())
    }

    fun `test wrap in withContext`() {
        myFixture.configureByText(
            "wrapInWithContext.kt",
            """                            
                suspend fun wrapWithContextFix() {
                    val variable = <warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">blo<caret>ck</warning>()
                    print(variable)
                }
            """.trimIndent()
        )
        myFixture.checkHighlighting()

        val action = myFixture.getAvailableIntention("Wrap call in 'withContext'")
        myFixture.launchAction(action!!)

        myFixture.checkResult(
            """
                import kotlinx.coroutines.Dispatchers
                import kotlinx.coroutines.withContext

                suspend fun wrapWithContextFix() {
                    val variable = withContext(Dispatchers.IO) {
                        block()
                    }
                    print(variable)
                }
            """.trimIndent()
        )
    }

    fun `test add dispatcher in flow generator`() {
        myFixture.configureByText(
            "ioDispatcherInFlow.kt",
            """
            import kotlinx.coroutines.flow.flow

            fun flowFix() {
                flow<Int> { <warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">blo<caret>ck</warning>() }
            }
        """.trimIndent()
        )
        myFixture.checkHighlighting()

        val action = myFixture.getAvailableIntention("Flow on 'Dispatchers.IO'")
        myFixture.launchAction(action!!)

        myFixture.checkResult(
            """
                import kotlinx.coroutines.Dispatchers
                import kotlinx.coroutines.flow.flow
                import kotlinx.coroutines.flow.flowOn

                fun flowFix() {
                    flow<Int> { block() }
                        .flowOn(Dispatchers.IO)
                }
            """.trimIndent()
        )
    }

    fun `test add dispatcher in flow`() {
        myFixture.configureByText(
            "ioDispatcherInFlow.kt",
            """
            import kotlinx.coroutines.Dispatchers
            import kotlinx.coroutines.flow.flowOf
            import kotlinx.coroutines.flow.flowOn
            import kotlinx.coroutines.flow.map

            fun flowFix() {
                flowOf(1,2,4)
                    .map { 
                        <warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">blo<caret>ck</warning>()
                    }
            }
        """.trimIndent()
        )
        myFixture.checkHighlighting()

        val action = myFixture.getAvailableIntention("Flow on 'Dispatchers.IO'")
        myFixture.launchAction(action!!)

        myFixture.checkResult(
            """
            import kotlinx.coroutines.Dispatchers
            import kotlinx.coroutines.flow.flowOf
            import kotlinx.coroutines.flow.flowOn
            import kotlinx.coroutines.flow.map

            fun flowFix() {
                flowOf(1, 2, 4)
                    .map {
                        block()
                    }
                    .flowOn(Dispatchers.IO)
            }
        """.trimIndent()
        )
    }

    fun `test add flowOn to flow generator`() {
        myFixture.configureByText(
            "ioDispatcherInFlow.kt",
            """
            import kotlinx.coroutines.flow.flow

            fun flowFix() {
                flow<Int> { <warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">blo<caret>ck</warning>()}
            }
        """.trimIndent()
        )
        myFixture.checkHighlighting()

        val action = myFixture.getAvailableIntention("Flow on 'Dispatchers.IO'")
        myFixture.launchAction(action!!)

        myFixture.checkResult(
            """
            import kotlinx.coroutines.Dispatchers
            import kotlinx.coroutines.flow.flow
            import kotlinx.coroutines.flow.flowOn

            fun flowFix() {
                flow<Int> { block() }
                    .flowOn(Dispatchers.IO)
            }
        """.trimIndent()
        )
    }

    fun `test replace unknown dispatcher in flow`() {
        myFixture.configureByText(
            "ioDispatcherInFlow.kt",
            """
            import kotlinx.coroutines.Dispatchers
            import kotlinx.coroutines.flow.flowOf
            import kotlinx.coroutines.flow.flowOn
            import kotlinx.coroutines.flow.map

            fun flowFix() {
                flowOf(1,2,4)
                    .map { 
                        <warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">blo<caret>ck</warning>()
                    }
                    .flowOn(Dispatchers.Main)
            }
        """.trimIndent()
        )
        myFixture.checkHighlighting()

        val action = myFixture.getAvailableIntention("Flow on 'Dispatchers.IO'")
        myFixture.launchAction(action!!)

        myFixture.checkResult(
            """
            import kotlinx.coroutines.Dispatchers
            import kotlinx.coroutines.flow.flowOf
            import kotlinx.coroutines.flow.flowOn
            import kotlinx.coroutines.flow.map

            fun flowFix() {
                flowOf(1,2,4)
                    .map { 
                        block()
                    }
                    .flowOn(Dispatchers.IO)
            }
        """.trimIndent()
        )
    }

    fun `test replace unknown dispatcher in withContext`() {
        myFixture.configureByText(
            "ioDispatcherSwitch.kt",
            """
            import kotlinx.coroutines.Dispatchers
            import kotlinx.coroutines.withContext

            suspend fun switchContext() {
                withContext(Dispatchers.Main) {
                    <warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">blo<caret>ck</warning>()
                }
            }
        """.trimIndent()
        )
        myFixture.checkHighlighting()
        val action = myFixture.getAvailableIntention("Switch to 'Dispatchers.IO' context")

        myFixture.launchAction(action!!)
        myFixture.checkResult(
            """
            import kotlinx.coroutines.Dispatchers
            import kotlinx.coroutines.withContext

            suspend fun switchContext() {
                withContext(Dispatchers.IO) {
                    block()
                }
            }
        """.trimIndent()
        )
    }

    fun `test wrap dot qualified expression`() {
        myFixture.configureByText(
            "dotQualified.kt",
            """
            import kotlinx.coroutines.Dispatchers
            import kotlinx.coroutines.withContext
            import org.jetbrains.annotations.Blocking

            class Foo {
                @Blocking
                fun bar() {}
            }

            suspend fun wrapWithContextFix() {
                val variable = Foo().<warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">ba<caret>r</warning>()
                print(variable)
            }
        """.trimIndent()
        )
        myFixture.checkHighlighting()
        val action = myFixture.getAvailableIntention("Wrap call in 'withContext'")

        myFixture.launchAction(action!!)
        myFixture.checkResult(
            """
            import kotlinx.coroutines.Dispatchers
            import kotlinx.coroutines.withContext
            import org.jetbrains.annotations.Blocking

            class Foo {
                @Blocking
                fun bar() {}
            }

            suspend fun wrapWithContextFix() {
                val variable = withContext(Dispatchers.IO) {
                    Foo().bar()
                }
                print(variable)
            }
        """.trimIndent()
        )
    }

    fun `test no fixes in non-suspendable context`() {
        myFixture.configureByText(
            "noIntentions.kt",
            """
            import kotlinx.coroutines.Dispatchers
            import kotlinx.coroutines.withContext
            import org.jetbrains.annotations.NonBlocking

            fun acceptSimpleBlock(block: () -> Unit) { block() }

            @NonBlocking
            fun differentContexts() {
                acceptSimpleBlock {
                    <warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">block</warning>()  
                }
            }
        """.trimIndent()
        )
        myFixture.checkHighlighting()
        Assert.assertTrue(myFixture.availableIntentions.isEmpty())
    }

    fun `test consider unknown contexts blocking intention`() {
        myFixture.allowTreeAccessForAllFiles()
        myFixture.configureByText(
            "UnknownContext.kt",
            """
                <info descr="null">import</info> kotlinx.coroutines.<info descr="null">Dispatchers</info>
                <info descr="null">import</info> kotlinx.coroutines.withContext
                <info descr="null">import</info> kotlin.coroutines.<info descr="null">CoroutineContext</info>
                
                class <info descr="null">CustomContext</info>: <info descr="null">CoroutineContext</info> {
                    <info descr="null">override</info> fun <<info descr="null">R</info>> <info descr="null">fold</info>(<info descr="null">initial</info>: <info descr="null">R</info>, <info descr="null">operation</info>: (<info descr="null">R</info>, <info descr="null">CoroutineContext</info>.<info descr="null">Element</info>) -> <info descr="null">R</info>): <info descr="null">R</info> = <info descr="TODO()"><info descr="null">TODO</info>()</info>
                    <info descr="null">override</info> fun <<info descr="null">E</info> : <info descr="null">CoroutineContext</info>.<info descr="null">Element</info>> <info descr="null">get</info>(<info descr="null">key</info>: <info descr="null">CoroutineContext</info>.<info descr="null">Key</info><<info descr="null">E</info>>): <info descr="null">E</info>? = <info descr="TODO()"><info descr="null">TODO</info>()</info>
                    <info descr="null">override</info> fun <info descr="null">minusKey</info>(<info descr="null">key</info>: <info descr="null">CoroutineContext</info>.<info descr="null">Key</info><*>): <info descr="null">CoroutineContext</info> = <info descr="TODO()"><info descr="null">TODO</info>()</info>
                }
                
                <info descr="null">suspend</info> fun <info descr="null">unknownContext</info>() {
                    <info descr="null">withContext</info>(<info descr="null">CustomContext</info>()) {
                        <info descr="null"><info descr="Consider unknown contexts non-blocking">blo<caret>ck</info></info>()
                    }
                }
        """.trimIndent()
        )
        myFixture.checkHighlighting(false, true, false)
        val action = myFixture.getAvailableIntention("Consider unknown contexts non-blocking")

        myFixture.launchAction(action!!)
        val warning = myFixture.doHighlighting(HighlightSeverity.WARNING)
            .firstOrNull {
                it.description == "Possibly blocking call in non-blocking context could lead to thread starvation" && it.range.equalsToRange(511, 516)
            }

        Assert.assertNotNull("Inspection should report blocking call with unknown contexts considered non-blocking", warning)
        val reverseAction = myFixture.getAvailableIntention("Consider unknown contexts blocking")
        myFixture.launchAction(reverseAction!!)
        val info = myFixture.doHighlighting(HighlightSeverity.INFORMATION)
            .firstOrNull {
                it.description == "Consider unknown contexts non-blocking" && it.range.equalsToRange(511, 516)
            }
        Assert.assertNotNull("Inspection should NOT report blocking call with unknown contexts considered blocking," +
                                     " but should have intention to change behaviour instead", info)
    }
}